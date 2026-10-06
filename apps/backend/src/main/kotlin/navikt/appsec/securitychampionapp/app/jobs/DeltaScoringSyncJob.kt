package navikt.appsec.securitychampionapp.app.jobs

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
) {
    private val logger = LoggerFactory.getLogger(DeltaScoringSyncJob::class.java)

    @Scheduled(cron = "0 30 */6 * * *")
    fun syncDeltaScoring() {
        if (!enabled) return

        jobLock.runWithLock(DELTA_SCORING_JOB_LOCK_KEY, "syncDeltaScoring", ::runSync)
    }

    fun triggerManualSync(): SyncTriggerResult {
        if (!enabled) return SyncTriggerResult.DISABLED
        return syncTrigger.trigger(DELTA_SCORING_JOB_LOCK_KEY, "syncDeltaScoring", ::runSync)
    }

    private fun runSync() {
        try {
            val summary = deltaScoringService.sync()
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
            logger.warn("Delta registration sync failed: {}", e.failure.summary)
        } catch (_: DataAccessException) {
            logger.error("Delta registration sync failed because scoring persistence is unavailable")
        } catch (e: Exception) {
            logger.error("Delta registration sync failed unexpectedly", e)
        }
    }
}
