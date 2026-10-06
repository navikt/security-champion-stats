package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventRegistrations
import navikt.appsec.securitychampionapp.integrations.delta.DeltaFailure
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import navikt.appsec.securitychampionapp.integrations.delta.DeltaRegistrationSource
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEligibleCategoryRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEventMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaScoringStatusRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext

private val DELTA_SCORING_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

@Service
class DeltaScoringService(
    private val eventSource: DeltaRegistrationSource,
    private val mappingRepository: DeltaEventMappingRepository,
    private val categoryRepository: DeltaEligibleCategoryRepository,
    private val participantRepository: ProgramParticipantRepository,
    private val scoringService: ScoringService,
    private val statusRepository: DeltaScoringStatusRepository,
    private val clock: Clock,
) {
    fun sync(run: AuditRunContext? = null): DeltaSyncSummary {
        val attemptStartedAt = clock.instant()
        statusRepository.recordStarted(attemptStartedAt)
        return try {
            val summary = syncMappedEvents(run?.correlationId)
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
        } catch (e: Exception) {
            statusRepository.recordFailed(clock.instant(), "Delta registration sync failed unexpectedly")
            throw e
        }
    }

    private fun syncMappedEvents(auditCorrelationId: UUID?): DeltaSyncSummary {
        val categories = categoryRepository.findAll()
        val mappings = mappingRepository.findAll()
        if (categories.isEmpty() && mappings.isEmpty()) return DeltaSyncSummary()

        val now = LocalDateTime.now(clock.withZone(DELTA_SCORING_ZONE))
        val from = LocalDate.of(now.year, 1, 1).atStartOfDay()
        val activeParticipants = participantRepository.findActiveParticipants()
        if (!activeParticipants.isOk) {
            throw DeltaIntegrationException(DeltaFailure.PARTICIPANT_LOOKUP)
        }
        val participantsByEmail = activeParticipants.queryResult
            .filter { it.email.isNotBlank() }
            .groupBy { it.email.normalizeEmail() }

        var failedSources = 0
        val failureSummaries = linkedSetOf<String>()
        val events = linkedMapOf<UUID, DeltaEventRegistrations>()

        fun fetch(block: () -> List<DeltaEventRegistrations>) {
            try {
                block().forEach { events.putIfAbsent(it.eventUuid, it) }
            } catch (e: DeltaIntegrationException) {
                if (e.failure == DeltaFailure.TOKEN || e.failure == DeltaFailure.CONFIGURATION) throw e
                failedSources++
                failureSummaries += e.failure.summary
            }
        }

        categories.forEach { category -> fetch { eventSource.pastEventsInCategory(category.categoryId) } }
        mappings.filterNot { it.deltaEventUuid in events }.forEach { mapping ->
            fetch {
                listOf(
                    eventSource.event(mapping.deltaEventUuid)
                        ?: throw DeltaIntegrationException(DeltaFailure.EVENT_NOT_FOUND),
                )
            }
        }

        val eligibleEvents = events.values.filter { it.startTime >= from && it.startTime < now }
        var creditsAwarded = 0
        var duplicateCredits = 0
        var unmatchedRegistrations = 0

        eligibleEvents.forEach { event ->
            event.participantEmails.map { it.normalizeEmail() }.toSet().forEach emailLoop@{ email ->
                val participants = participantsByEmail[email] ?: return@emailLoop
                val participant = participants.singleOrNull()
                if (participant == null) {
                    unmatchedRegistrations++
                    return@emailLoop
                }
                when (
                    scoringService.awardCredit(
                        participantId = UUID.fromString(participant.id),
                        creditType = ActivityCreditType.DELTA_REGISTRATION,
                        uniquenessKey = event.eventUuid.toString(),
                        sourceReference = event.eventUuid.toString(),
                        auditCorrelationId = auditCorrelationId,
                    )
                ) {
                    CreditAwardResult.AWARDED -> creditsAwarded++
                    CreditAwardResult.DUPLICATE -> duplicateCredits++
                    CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING -> unmatchedRegistrations++
                }
            }
        }

        return DeltaSyncSummary(
            eventsScanned = eligibleEvents.size,
            creditsAwarded = creditsAwarded,
            duplicateCredits = duplicateCredits,
            unmatchedRegistrations = unmatchedRegistrations,
            failedEvents = failedSources,
            failureSummary = when (failureSummaries.size) {
                0 -> null
                1 -> failureSummaries.single()
                else -> "Some Delta categories or events could not be synchronized"
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
