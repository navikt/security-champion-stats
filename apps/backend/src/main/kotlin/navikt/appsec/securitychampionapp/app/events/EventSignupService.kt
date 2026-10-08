package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class EventSignupStatus { SIGNED_UP, NOT_SIGNED_UP, HOST, UNAVAILABLE }

data class DeltaSignupRoster(
    val eventId: UUID,
    val startsAt: Instant,
    val participantEmails: Set<String>,
    val hostEmails: Set<String>,
)

fun interface DeltaSignupSource {
    fun signupRoster(eventId: UUID): DeltaSignupRoster
}

class EventSignupUnavailableException : RuntimeException("Delta signup information is unavailable")

fun deltaSignupEventId(event: Event): UUID? {
    if (event.deltaEvent) return runCatching { UUID.fromString(event.id) }.getOrNull()
    val uri = event.link?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (uri.scheme != "https" || !uri.host.equals("delta.nav.no", ignoreCase = true)) return null
    val id = Regex("/event/([a-fA-F0-9-]{36})/?").matchEntire(uri.path)?.groupValues?.get(1) ?: return null
    return runCatching { UUID.fromString(id) }.getOrNull()
}

fun isUpcomingEvent(event: Event, clock: Clock): Boolean =
    if (event.allDay) LocalDate.parse(event.endDate) >= LocalDate.now(clock.withZone(ZoneId.of("Europe/Oslo")))
    else Instant.parse(event.startDate).isAfter(clock.instant())

@Service
class EventSignupService(
    private val source: DeltaSignupSource,
    private val participants: ParticipantStore,
    private val clock: Clock,
) {
    private data class Snapshot(val roster: DeltaSignupRoster, val checkedAt: Instant)
    private val snapshots = ConcurrentHashMap<UUID, Snapshot>()
    private val logger = LoggerFactory.getLogger(EventSignupService::class.java)

    fun forParticipant(events: List<Event>, email: String): List<Event> {
        val supported = events.map {
            it.copy(signupSupported = deltaSignupEventId(it) != null && isUpcomingEvent(it, clock))
        }
        if (participants.findByNavNoEmail(email)?.status != ParticipationStatus.ACTIVE) return supported
        snapshots.entries.removeIf { it.value.checkedAt.plusSeconds(60).isBefore(clock.instant()) }
        return supported.map { event ->
            val id = deltaSignupEventId(event)
            if (id == null || !isUpcomingEvent(event, clock)) return@map event
            try {
                val snapshot = requireNotNull(snapshots.compute(id) { _, previous ->
                    if (previous != null && previous.checkedAt.plusSeconds(60).isAfter(clock.instant())) previous
                    else Snapshot(source.signupRoster(id), clock.instant())
                })
                val identity = email.trim().lowercase()
                event.copy(
                    signupStatus = when (identity) {
                        in snapshot.roster.hostEmails -> EventSignupStatus.HOST
                        in snapshot.roster.participantEmails -> EventSignupStatus.SIGNED_UP
                        else -> EventSignupStatus.NOT_SIGNED_UP
                    },
                    signupCheckedAt = snapshot.checkedAt.toString(),
                )
            } catch (_: EventSignupUnavailableException) {
                logger.warn("Delta signup lookup unavailable (event={})", id)
                event.copy(signupStatus = EventSignupStatus.UNAVAILABLE)
            }
        }
    }
}
