package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import navikt.appsec.securitychampionapp.integrations.delta.DeltaFailure
import navikt.appsec.securitychampionapp.integrations.delta.DeltaRegistrationSource
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEventMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaScoringStatusRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val DELTA_SCORING_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

@Service
class DeltaScoringService(
    private val eventSource: DeltaRegistrationSource,
    private val mappingRepository: DeltaEventMappingRepository,
    private val participantRepository: ProgramParticipantRepository,
    private val scoringService: ScoringService,
    private val statusRepository: DeltaScoringStatusRepository,
    private val clock: Clock,
) {
    fun sync(): DeltaSyncSummary {
        val attemptStartedAt = clock.instant()
        statusRepository.recordStarted(attemptStartedAt)
        return try {
            val summary = syncMappedEvents()
            if (summary.failedEvents == 0) {
                statusRepository.recordSucceeded(clock.instant(), summary)
            } else {
                statusRepository.recordPartialFailure(clock.instant(), summary)
            }
            summary
        } catch (e: DeltaIntegrationException) {
            statusRepository.recordFailed(clock.instant(), e.failure.summary)
            throw e
        } catch (e: DataAccessException) {
            statusRepository.recordFailed(
                clock.instant(),
                "Delta registration sync failed because scoring persistence is unavailable",
            )
            throw e
        }
    }

    private fun syncMappedEvents(): DeltaSyncSummary {
        val mappings = mappingRepository.findAll()
        if (mappings.isEmpty()) return DeltaSyncSummary()

        val activeParticipants = participantRepository.findActiveParticipants()
        if (!activeParticipants.isOk) {
            throw DeltaIntegrationException(DeltaFailure.PARTICIPANT_LOOKUP)
        }
        val participantsByEmail = activeParticipants.queryResult
            .filter { it.email.isNotBlank() }
            .groupBy { it.email.normalizeEmail() }
        val currentYear = LocalDate.now(clock.withZone(DELTA_SCORING_ZONE)).year
        var eventsScanned = 0
        var creditsAwarded = 0
        var duplicateCredits = 0
        var unmatchedRegistrations = 0
        var failedEvents = 0
        val failureSummaries = linkedSetOf<String>()

        mappings.forEach { mapping ->
            val roster = try {
                eventSource.fetchEvent(mapping.deltaEventUuid).also {
                    if (it.eventUuid != mapping.deltaEventUuid) {
                        throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
                    }
                }
            } catch (e: DeltaIntegrationException) {
                failedEvents++
                failureSummaries += e.failure.summary
                return@forEach
            }
            if (roster.startTime.year != currentYear) return@forEach
            eventsScanned++

            roster.participantEmails
                .map { it.normalizeEmail() }
                .distinct()
                .forEach { email ->
                    val participant = participantsByEmail[email]?.singleOrNull()
                    if (participant == null) {
                        unmatchedRegistrations++
                        return@forEach
                    }

                    when (
                        scoringService.awardCredit(
                            participantId = java.util.UUID.fromString(participant.id),
                            creditType = ActivityCreditType.DELTA_REGISTRATION,
                            uniquenessKey = mapping.deltaEventUuid.toString(),
                            sourceReference = mapping.deltaEventUuid.toString(),
                        )
                    ) {
                        CreditAwardResult.AWARDED -> creditsAwarded++
                        CreditAwardResult.DUPLICATE -> duplicateCredits++
                        CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING -> unmatchedRegistrations++
                    }
                }
        }

        return DeltaSyncSummary(
            eventsScanned = eventsScanned,
            creditsAwarded = creditsAwarded,
            duplicateCredits = duplicateCredits,
            unmatchedRegistrations = unmatchedRegistrations,
            failedEvents = failedEvents,
            failureSummary = when (failureSummaries.size) {
                0 -> null
                1 -> failureSummaries.single()
                else -> "Some mapped Delta events could not be synchronized"
            },
        )
    }

    private fun String.normalizeEmail(): String = trim().lowercase(Locale.ROOT)
}

data class DeltaSyncSummary(
    val eventsScanned: Int = 0,
    val creditsAwarded: Int = 0,
    val duplicateCredits: Int = 0,
    val unmatchedRegistrations: Int = 0,
    val failedEvents: Int = 0,
    val failureSummary: String? = null,
)
