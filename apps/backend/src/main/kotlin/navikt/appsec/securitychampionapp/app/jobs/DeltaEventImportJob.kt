package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.events.DeltaEventImportService
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val DELTA_EVENT_IMPORT_JOB_LOCK_KEY = 1_005L

@Component
class DeltaEventImportJob(
    private val jobLock: PostgresJobLock,
    private val syncTrigger: ScoringSyncTrigger,
    private val importService: DeltaEventImportService,
    @Value($$"${delta.events.enabled:false}") private val enabled: Boolean,
    private val auditService: ProgramAuditService? = null,
) {
    private val logger = LoggerFactory.getLogger(DeltaEventImportJob::class.java)

    @Scheduled(cron = "0 15 */6 * * *")
    fun importDeltaEvents() {
        if (!enabled) return

        jobLock.runWithLock(DELTA_EVENT_IMPORT_JOB_LOCK_KEY, "importDeltaEvents") {
            runImport(AuditRunContext())
        }
    }

    fun triggerManualImport(actorNavNoEmail: String? = null): SyncTriggerResult {
        if (!enabled) return SyncTriggerResult.DISABLED
        if (actorNavNoEmail == null) {
            return syncTrigger.trigger(DELTA_EVENT_IMPORT_JOB_LOCK_KEY, "importDeltaEvents") {
                runImport(AuditRunContext())
            }
        }
        return syncTrigger.trigger(DELTA_EVENT_IMPORT_JOB_LOCK_KEY, "importDeltaEvents", actorNavNoEmail, ::runImport)
    }

    private fun runImport(run: AuditRunContext) {
        auditService?.recordRun("DELTA_EVENT_IMPORT_STARTED", AuditOutcome.SUCCEEDED, run)
        try {
            val summary = importService.import()
            auditService?.recordRun(
                "DELTA_EVENT_IMPORT_COMPLETED",
                AuditOutcome.SUCCEEDED,
                run,
                mapOf(
                    "eventsFetched" to summary.eventsFetched,
                    "eventsSaved" to summary.eventsSaved,
                    "conflicts" to summary.conflicts,
                ),
            )
            logger.info(
                "Delta event import completed: fetched={}, saved={}, conflicts={}",
                summary.eventsFetched,
                summary.eventsSaved,
                summary.conflicts,
            )
        } catch (e: DeltaIntegrationException) {
            auditService?.recordRun(
                "DELTA_EVENT_IMPORT_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to e.failure.name),
            )
            logger.warn("Delta event import failed: {}", e.failure.summary)
        } catch (_: DataAccessException) {
            auditService?.recordRun(
                "DELTA_EVENT_IMPORT_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to "persistence"),
            )
            logger.error("Delta event import failed because event persistence is unavailable")
        } catch (e: Exception) {
            auditService?.recordRun(
                "DELTA_EVENT_IMPORT_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to "unexpected"),
            )
            logger.error("Delta event import failed unexpectedly", e)
        }
    }
}
