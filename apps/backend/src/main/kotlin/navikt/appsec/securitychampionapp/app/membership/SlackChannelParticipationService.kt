package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.DeactivationReason
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

interface SlackChannelGateway {
    fun channelMembers(channelId: String): Set<String>
    fun notifyChannelDeparture(slackUserId: String, channelId: String, deliveryId: UUID): String
}

data class ChannelObservation(
    val participantId: UUID,
    val slackUserId: String?,
    val present: Boolean?,
    val absentSince: Instant?,
)

data class ChannelCheckPlan(
    val observations: List<ChannelObservation>,
    val departures: Map<UUID, String>,
    val returns: Set<UUID>,
)

data class ChannelCheckChanges(
    val deactivated: Set<UUID>,
    val reactivated: Set<UUID>,
)

enum class ChannelNoticeStatus { PENDING, SENDING, SENT, CANCELLED, UNCERTAIN }

data class ChannelDepartureNotice(
    val id: UUID,
    val participantId: UUID,
    val slackUserId: String,
    val status: ChannelNoticeStatus,
)

interface SlackChannelParticipationStore {
    fun observations(channelId: String): Map<UUID, ChannelObservation>
    fun apply(channelId: String, checkedAt: Instant, plan: ChannelCheckPlan): ChannelCheckChanges
    fun recoverInterruptedNotices(channelId: String)
    fun dueNotices(channelId: String): List<ChannelDepartureNotice>
    fun updateNotice(id: UUID, status: ChannelNoticeStatus, messageTs: String? = null)
    fun deferNotice(id: UUID, retryAfter: Duration)
    fun outstandingNotices(channelId: String): Int
}

data class SlackChannelParticipationSettings(
    val channelId: String,
    val maxDeparturesPerCheck: Int,
) {
    fun validate() {
        require(channelId.isNotBlank()) { "The Security Champions Slack channel ID must be configured" }
        require(maxDeparturesPerCheck > 0) { "The channel departure limit must be positive" }
    }
}

data class ChannelCheckSummary(
    val checkedParticipants: Int,
    val absent: Int,
    val unresolved: Int,
    val deactivated: Set<UUID>,
    val reactivated: Set<UUID>,
    val noticesSent: Int,
    val noticesOutstanding: Int,
)

class ChannelDepartureLimitException(departures: Int, limit: Int) : IllegalStateException(
    "Slack channel check found $departures departures, above the limit of $limit; no participants were changed",
)

class SlackChannelParticipationService(
    private val participants: ParticipantStore,
    private val directory: SlackParticipantDirectory,
    private val slack: SlackChannelGateway,
    private val store: SlackChannelParticipationStore,
    private val settings: SlackChannelParticipationSettings,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(SlackChannelParticipationService::class.java)

    fun check(): ChannelCheckSummary {
        settings.validate()
        val inScope = participants.findAllParticipants().filter {
            it.status == ParticipationStatus.ACTIVE ||
                (it.status == ParticipationStatus.DEACTIVATED &&
                    it.deactivationReason == DeactivationReason.SLACK_CHANNEL_DEPARTURE)
        }
        val members = slack.channelMembers(settings.channelId)
        check(members.isNotEmpty()) { "Slack channel membership read returned no members" }
        val resolution = if (inScope.isEmpty()) {
            SlackIdentityResolution(emptyMap(), emptySet())
        } else {
            directory.resolve(inScope)
        }
        val previous = store.observations(settings.channelId)
        val now = clock.instant()

        val observations = mutableListOf<ChannelObservation>()
        val departures = mutableMapOf<UUID, String>()
        val returns = mutableSetOf<UUID>()
        inScope.forEach { participant ->
            val slackUserId = resolution.users[participant.id]
            val prior = previous[participant.id]?.takeIf { slackUserId != null && it.slackUserId == slackUserId }
            when {
                slackUserId == null -> observations += ChannelObservation(participant.id, null, null, null)
                slackUserId in members -> {
                    observations += ChannelObservation(participant.id, slackUserId, true, null)
                    if (participant.status == ParticipationStatus.DEACTIVATED) returns += participant.id
                }
                else -> {
                    observations += ChannelObservation(participant.id, slackUserId, false, prior?.absentSince ?: now)
                    if (participant.status == ParticipationStatus.ACTIVE && prior?.present == true) {
                        departures[participant.id] = slackUserId
                    }
                }
            }
        }
        if (departures.size > settings.maxDeparturesPerCheck) {
            throw ChannelDepartureLimitException(departures.size, settings.maxDeparturesPerCheck)
        }

        val changes = store.apply(settings.channelId, now, ChannelCheckPlan(observations, departures, returns))
        val sent = deliverNotices()
        return ChannelCheckSummary(
            checkedParticipants = inScope.size,
            absent = observations.count { it.present == false },
            unresolved = observations.count { it.present == null },
            deactivated = changes.deactivated,
            reactivated = changes.reactivated,
            noticesSent = sent,
            noticesOutstanding = store.outstandingNotices(settings.channelId),
        )
    }

    private fun deliverNotices(): Int {
        store.recoverInterruptedNotices(settings.channelId)
        var sent = 0
        for (notice in store.dueNotices(settings.channelId)) {
            val participant = participants.findById(notice.participantId)
            if (participant?.deactivationReason != DeactivationReason.SLACK_CHANNEL_DEPARTURE) {
                store.updateNotice(notice.id, ChannelNoticeStatus.CANCELLED)
                continue
            }
            store.updateNotice(notice.id, ChannelNoticeStatus.SENDING)
            val messageTs = try {
                slack.notifyChannelDeparture(notice.slackUserId, settings.channelId, notice.id)
            } catch (e: MembershipDeliveryException) {
                if (e.uncertain) {
                    store.updateNotice(notice.id, ChannelNoticeStatus.UNCERTAIN)
                    logger.warn("Slack channel departure notice delivery is unconfirmed (delivery={})", notice.id)
                    break
                }
                store.deferNotice(notice.id, e.retryAfter)
                logger.warn("Slack channel departure notice rejected (delivery={}): {}", notice.id, e.message)
                if (e.stopBatch) break
                continue
            }
            store.updateNotice(notice.id, ChannelNoticeStatus.SENT, messageTs)
            sent++
        }
        return sent
    }
}
