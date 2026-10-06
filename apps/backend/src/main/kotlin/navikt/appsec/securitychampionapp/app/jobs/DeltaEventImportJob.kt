package navikt.appsec.securitychampionapp.app.jobs

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
) {
    private val logger = LoggerFactory.getLogger(DeltaEventImportJob::class.java)

    @Scheduled(cron = "0 15 */6 * * *")
    fun importDeltaEvents() {
        if (!enabled) return

        jobLock.runWithLock(DELTA_EVENT_IMPORT_JOB_LOCK_KEY, "importDeltaEvents", ::runImport)
    }

    fun triggerManualImport(): SyncTriggerResult {
        if (!enabled) return SyncTriggerResult.DISABLED
        return syncTrigger.trigger(DELTA_EVENT_IMPORT_JOB_LOCK_KEY, "importDeltaEvents", ::runImport)
    }

    private fun runImport() {
        try {
            val summary = importService.import()
            logger.info(
                "Delta event import completed: fetched={}, saved={}, conflicts={}",
                summary.eventsFetched,
                summary.eventsSaved,
                summary.conflicts,
            )
        } catch (e: DeltaIntegrationException) {
            logger.warn("Delta event import failed: {}", e.failure.summary)
        } catch (_: DataAccessException) {
            logger.error("Delta event import failed because event persistence is unavailable")
        } catch (e: Exception) {
            logger.error("Delta event import failed unexpectedly", e)
        }
    }
}
