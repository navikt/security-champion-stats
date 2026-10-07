package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.GitHubScoringService
import navikt.appsec.securitychampionapp.integrations.github.GitHubIntegrationException
import navikt.appsec.securitychampionapp.integrations.postgress.GitHubScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class GitHubScoringSyncJob(
    private val jobLock: PostgresJobLock,
    private val trigger: ScoringSyncTrigger,
    private val service: GitHubScoringService,
    private val status: GitHubScoringStatusRepository,
    private val audit: ProgramAuditService,
    private val clock: Clock,
    @Value($$"${github.scoring.enabled:false}") private val enabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(GitHubScoringSyncJob::class.java)

    @Scheduled(cron = $$"${github.scoring.cron:0 15 */6 * * *}")
    fun syncGitHubScoring() {
        if (!enabled) return
        jobLock.runWithLock(ScoringJobLockKeys.GITHUB, "syncGitHubScoring") { runSync(AuditRunContext()) }
    }

    fun triggerManualSync(actorEmail: String): SyncTriggerResult {
        if (!enabled) return SyncTriggerResult.DISABLED
        return trigger.trigger(ScoringJobLockKeys.GITHUB, "syncGitHubScoring", actorEmail, ::runSync)
    }

    private fun runSync(run: AuditRunContext) {
        audit.recordRun("GITHUB_SCORING_SYNC_STARTED", AuditOutcome.SUCCEEDED, run)
        try {
            status.recordStarted(clock.instant())
            val summary = service.sync(clock.instant(), run.correlationId)
            status.recordSucceeded(clock.instant(), summary)
            audit.recordRun(
                "GITHUB_SCORING_SYNC_COMPLETED", AuditOutcome.SUCCEEDED, run,
                mapOf(
                    "contributionsScanned" to summary.contributionsScanned,
                    "creditsAwarded" to summary.creditsAwarded,
                    "duplicateCredits" to summary.duplicateCredits,
                    "unmappedAuthors" to summary.unmappedAuthors,
                ),
            )
            logger.info(
                "GitHub scoring sync completed: scanned={}, awarded={}, duplicates={}, unmapped={}",
                summary.contributionsScanned, summary.creditsAwarded, summary.duplicateCredits, summary.unmappedAuthors,
            )
        } catch (e: GitHubIntegrationException) {
            recordFailure(run, e.failure.summary, e.reason)
        } catch (_: DataAccessException) {
            recordFailure(run, "GitHub scoring could not persist sync results")
        } catch (_: IllegalStateException) {
            recordFailure(run, "GitHub scoring state changed or configuration is incomplete; retry the sync")
        } catch (e: Exception) {
            logger.error("GitHub scoring sync failed unexpectedly: {}", e.javaClass.simpleName)
            recordFailure(run, "GitHub scoring sync failed unexpectedly")
        }
    }

    private fun recordFailure(run: AuditRunContext, summary: String, reason: String? = null) {
        if (reason == null) {
            logger.warn("GitHub scoring sync failed: {}", summary)
        } else {
            logger.warn("GitHub scoring sync failed: {} (reason={})", summary, reason)
        }
        audit.recordRun("GITHUB_SCORING_SYNC_FAILED", AuditOutcome.FAILED, run, mapOf("failure" to summary))
        status.recordFailed(summary)
    }
}
