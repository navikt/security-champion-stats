package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringService
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val DELTA_SCORING_JOB_LOCK_KEY = ScoringJobLockKeys.DELTA

@Component
class DeltaScoringSyncJob(
    private val jobLock: PostgresJobLock,
    private val syncTrigger: ScoringSyncTrigger,
    private val deltaScoringService: DeltaScoringService,
    @Value($$"${delta.scoring.enabled:false}") private val enabled: Boolean,
    private val auditService: ProgramAuditService? = null,
) {
    private val logger = LoggerFactory.getLogger(DeltaScoringSyncJob::class.java)

    @Scheduled(cron = "0 30 */6 * * *")
    fun syncDeltaScoring() {
        if (!enabled) return

        jobLock.runWithLock(DELTA_SCORING_JOB_LOCK_KEY, "syncDeltaScoring") {
            runSync(AuditRunContext())
        }
    }

    fun triggerManualSync(actorNavNoEmail: String? = null): SyncTriggerResult {
        if (!enabled) return SyncTriggerResult.DISABLED
        if (actorNavNoEmail == null) {
            return syncTrigger.trigger(DELTA_SCORING_JOB_LOCK_KEY, "syncDeltaScoring") {
                runSync(AuditRunContext())
            }
        }
        return syncTrigger.trigger(DELTA_SCORING_JOB_LOCK_KEY, "syncDeltaScoring", actorNavNoEmail, ::runSync)
    }

    private fun runSync(run: AuditRunContext) {
        auditService?.recordRun("DELTA_SCORING_SYNC_STARTED", AuditOutcome.SUCCEEDED, run)
        try {
            val summary = deltaScoringService.sync(run)
            val outcome = if (summary.failedEvents > 0) AuditOutcome.PARTIAL else AuditOutcome.SUCCEEDED
            auditService?.recordRun(
                if (summary.failedEvents > 0) "DELTA_SCORING_SYNC_PARTIAL" else "DELTA_SCORING_SYNC_COMPLETED",
                outcome,
                run,
                mapOf(
                    "eventsScanned" to summary.eventsScanned,
                    "creditsAwarded" to summary.creditsAwarded,
                    "duplicateCredits" to summary.duplicateCredits,
                    "unmatchedRegistrations" to summary.unmatchedRegistrations,
                    "failedEvents" to summary.failedEvents,
                ),
            )
            if (summary.failedEvents > 0) {
                logger.warn(
                    "Delta registration sync partially failed: events={}, failedEvents={}, reason={}",
                    summary.eventsScanned,
                    summary.failedEvents,
                    summary.failureSummary,
                )
            } else {
                logger.info(
                    "Delta registration sync completed: events={}, awarded={}, duplicates={}, unmatched={}",
                    summary.eventsScanned,
                    summary.creditsAwarded,
                    summary.duplicateCredits,
                    summary.unmatchedRegistrations,
                )
            }
        } catch (e: DeltaIntegrationException) {
            auditService?.recordRun(
                "DELTA_SCORING_SYNC_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to e.failure.name),
            )
            logger.warn("Delta registration sync failed: {}", e.failure.summary)
        } catch (_: DataAccessException) {
            auditService?.recordRun(
                "DELTA_SCORING_SYNC_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to "persistence"),
            )
            logger.error("Delta registration sync failed because scoring persistence is unavailable")
        } catch (e: Exception) {
            auditService?.recordRun(
                "DELTA_SCORING_SYNC_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to "unexpected"),
            )
            logger.error("Delta registration sync failed unexpectedly", e)
        }
    }
}
