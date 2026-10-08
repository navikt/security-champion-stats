package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.membership.MembershipDeliveryException
import navikt.appsec.securitychampionapp.app.membership.SlackParticipantDirectory
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class ReminderDeliveryStatus { SENDING, SENT, FAILED, UNCERTAIN }
enum class ReminderRecipientStatus { READY, UNRESOLVED, ALREADY_SENT, DELIVERY_UNCERTAIN, RETRY_LATER }

data class ReminderDelivery(
    val participantId: UUID,
    val status: ReminderDeliveryStatus,
    val nextAttemptAt: Instant,
)

interface EventReminderStore {
    fun deliveries(eventId: UUID): List<ReminderDelivery>
    fun claim(eventId: UUID, participantId: UUID, slackUserId: String, deliveryId: UUID): Boolean
    fun finish(deliveryId: UUID, status: ReminderDeliveryStatus, messageTs: String? = null, retryAfter: Duration = Duration.ZERO)
}

fun interface EventReminderGateway {
    fun remind(slackUserId: String, text: String, deliveryId: UUID): String
}

data class EventReminderRecipient(
    val participantId: UUID,
    val name: String,
    val slackUserId: String?,
    val status: ReminderRecipientStatus,
)

data class EventReminderPreview(
    val version: String,
    val checkedAt: Instant,
    val message: String,
    val signedUpParticipants: Int,
    val recipients: List<EventReminderRecipient>,
)

data class EventReminderResult(val sent: Int, val failed: Int, val uncertain: Int, val skipped: Int)
class EventReminderConflictException(message: String) : RuntimeException(message)
class InvalidEventReminderMessageException :
    RuntimeException("The reminder message must contain text and be at most 4000 characters")

@Service
class EventReminderService(
    private val catalog: EventCatalogService,
    private val source: DeltaSignupSource,
    private val participants: ParticipantStore,
    private val directory: SlackParticipantDirectory,
    private val store: EventReminderStore,
    private val gateway: EventReminderGateway,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(EventReminderService::class.java)

    private fun event(id: String): Event = catalog.getAllEvents().firstOrNull { it.id == id }
        ?: throw EventReminderConflictException("The event is no longer in the catalog")

    fun preview(eventId: String): EventReminderPreview = prepare(eventId).second

    private fun prepare(eventId: String): Pair<UUID, EventReminderPreview> {
        val event = event(eventId)
        val deltaId = deltaSignupEventId(event)
            ?: throw EventReminderConflictException("Reminders are supported only for Delta events")
        if (!isUpcomingEvent(event, clock)) throw EventReminderConflictException("The event is no longer upcoming")
        val roster = source.signupRoster(deltaId)
        if (!roster.startsAt.isAfter(clock.instant())) {
            throw EventReminderConflictException("The Delta event has already started")
        }
        val active = participants.findActiveParticipants()
        val registered = roster.participantEmails + roster.hostEmails
        val unsigned = active.filter { it.navNoEmail.trim().lowercase() !in registered }
        val resolution = if (unsigned.isEmpty()) null else directory.resolve(unsigned)
        val deliveries = store.deliveries(deltaId).associateBy { it.participantId }
        val recipients = unsigned.sortedBy { it.id }.map { participant ->
            val delivery = deliveries[participant.id]
            val userId = resolution?.users?.get(participant.id)
            EventReminderRecipient(
                participant.id, participant.fullname, userId,
                when {
                    delivery?.status == ReminderDeliveryStatus.SENT -> ReminderRecipientStatus.ALREADY_SENT
                    delivery?.status in setOf(ReminderDeliveryStatus.SENDING, ReminderDeliveryStatus.UNCERTAIN) ->
                        ReminderRecipientStatus.DELIVERY_UNCERTAIN
                    delivery != null && delivery.nextAttemptAt.isAfter(clock.instant()) -> ReminderRecipientStatus.RETRY_LATER
                    userId == null -> ReminderRecipientStatus.UNRESOLVED
                    else -> ReminderRecipientStatus.READY
                },
            )
        }
        val startsAt = roster.startsAt.atZone(ZoneId.of("Europe/Oslo"))
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z", java.util.Locale.ENGLISH))
        val title = event.name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val message = "Reminder: $title on $startsAt. You haven't signed up in Delta yet. " +
            "<https://delta.nav.no/event/$deltaId|View the event and sign up>. " +
            "If you signed up just now, no action is needed."
        val fields = listOf(eventId, deltaId.toString(), message, active.size.toString(), registered.size.toString(), recipients.size.toString()) +
            active.sortedBy { it.id }.flatMap { listOf(it.id.toString(), it.navNoEmail, it.fullname) } +
            registered.sorted() +
            recipients.flatMap { listOf(it.participantId.toString(), it.slackUserId.orEmpty(), it.status.name) }
        val version = MessageDigest.getInstance("SHA-256")
            .digest(fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return deltaId to EventReminderPreview(version, clock.instant(), message, active.size - unsigned.size, recipients)
    }

    fun validate(eventId: String, expectedVersion: String, message: String): EventReminderPreview =
        validated(eventId, expectedVersion, message).second

    private fun validated(eventId: String, expectedVersion: String, message: String): Pair<UUID, EventReminderPreview> {
        if (message.isBlank() || message.length > 4000) throw InvalidEventReminderMessageException()
        val prepared = prepare(eventId)
        val preview = prepared.second
        if (expectedVersion.isBlank() || preview.version != expectedVersion) {
            throw EventReminderConflictException("Signup information or recipients changed. Preview reminders again")
        }
        if (preview.recipients.none { it.status == ReminderRecipientStatus.READY }) {
            throw EventReminderConflictException("No recipients are ready for a reminder")
        }
        return prepared
    }

    fun send(eventId: String, expectedVersion: String, message: String): EventReminderResult {
        val (deltaId, preview) = validated(eventId, expectedVersion, message)
        var sent = 0
        var failed = 0
        var uncertain = 0
        var skipped = preview.recipients.count {
            it.status in setOf(ReminderRecipientStatus.UNRESOLVED, ReminderRecipientStatus.DELIVERY_UNCERTAIN, ReminderRecipientStatus.RETRY_LATER)
        }
        val ready = preview.recipients.filter { it.status == ReminderRecipientStatus.READY }
        for ((index, recipient) in ready.withIndex()) {
            val participant = participants.findById(recipient.participantId)
            if (participant?.status != ParticipationStatus.ACTIVE) {
                skipped++
                continue
            }
            val deliveryId = UUID.randomUUID()
            if (!store.claim(deltaId, recipient.participantId, requireNotNull(recipient.slackUserId), deliveryId)) {
                skipped++
                continue
            }
            val messageTs = try {
                gateway.remind(recipient.slackUserId, message, deliveryId)
            } catch (e: MembershipDeliveryException) {
                store.finish(
                    deliveryId,
                    if (e.uncertain) ReminderDeliveryStatus.UNCERTAIN else ReminderDeliveryStatus.FAILED,
                    retryAfter = e.retryAfter,
                )
                if (e.uncertain) uncertain++ else failed++
                logger.warn("Slack event reminder delivery failed (delivery={}, uncertain={})", deliveryId, e.uncertain)
                if (e.stopBatch) {
                    skipped += ready.size - index - 1
                    break
                }
                continue
            }
            store.finish(deliveryId, ReminderDeliveryStatus.SENT, messageTs)
            sent++
        }
        return EventReminderResult(sent, failed, uncertain, skipped)
    }
}
