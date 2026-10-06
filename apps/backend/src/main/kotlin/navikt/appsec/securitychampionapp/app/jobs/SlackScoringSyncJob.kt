package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.SlackScoringService
import navikt.appsec.securitychampionapp.integrations.postgress.SlackScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.slack.SlackIntegrationException
import org.springframework.dao.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

private const val SLACK_SCORING_JOB_LOCK_KEY = ScoringJobLockKeys.SLACK

@Component
class SlackScoringSyncJob(
    private val jobLock: PostgresJobLock,
    private val syncTrigger: ScoringSyncTrigger,
    private val slackScoringService: SlackScoringService,
    private val statusRepository: SlackScoringStatusRepository,
    @Value($$"${slack.sc-channel-id}") private val channelId: String,
    private val clock: Clock,
    private val auditService: ProgramAuditService? = null,
) {
    private val logger = LoggerFactory.getLogger(SlackScoringSyncJob::class.java)

    @Scheduled(cron = $$"${slack.scoring.cron:0 0 */6 * * *}")
    fun syncSlackScoring() {
        jobLock.runWithLock(SLACK_SCORING_JOB_LOCK_KEY, "syncSlackScoring") {
            runSync(AuditRunContext())
        }
    }

    fun triggerManualSync(actorNavNoEmail: String? = null): SyncTriggerResult {
        if (actorNavNoEmail == null) {
            return syncTrigger.trigger(SLACK_SCORING_JOB_LOCK_KEY, "syncSlackScoring") {
                runSync(AuditRunContext())
            }
        }
        return syncTrigger.trigger(SLACK_SCORING_JOB_LOCK_KEY, "syncSlackScoring", actorNavNoEmail, ::runSync)
    }

    private fun runSync(run: AuditRunContext) {
        val attemptAt = clock.instant()
        auditService?.recordRun("SLACK_SCORING_SYNC_STARTED", AuditOutcome.SUCCEEDED, run)
        try {
            statusRepository.recordStarted(attemptAt)
            val summary = slackScoringService.sync(channelId, attemptAt, run.correlationId)
            statusRepository.recordSucceeded(clock.instant(), summary)
            auditService?.recordRun(
                "SLACK_SCORING_SYNC_COMPLETED",
                AuditOutcome.SUCCEEDED,
                run,
                mapOf(
                    "messagesScanned" to summary.messagesScanned,
                    "creditsAwarded" to summary.creditsAwarded,
                    "duplicateCredits" to summary.duplicateCredits,
                    "unmappedAuthors" to summary.unmappedAuthors,
                ),
            )
            logger.info(
                "Slack scoring sync completed: scanned={}, awarded={}, duplicates={}, unmapped={}",
                summary.messagesScanned,
                summary.creditsAwarded,
                summary.duplicateCredits,
                summary.unmappedAuthors,
            )
        } catch (e: SlackIntegrationException) {
            recordFailure(clock.instant(), requireNotNull(e.message), run, "integration")
        } catch (_: IllegalStateException) {
            recordFailure(clock.instant(), "Slack scoring configuration is incomplete", run, "configuration")
        } catch (_: DataAccessException) {
            recordFailure(clock.instant(), "Slack scoring could not persist sync results", run, "persistence")
        } catch (e: Exception) {
            logger.error("Slack scoring sync failed unexpectedly", e)
            auditService?.recordRun(
                "SLACK_SCORING_SYNC_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to "unexpected"),
            )
            statusRepository.recordFailed(clock.instant(), "Slack scoring sync failed unexpectedly")
        }
    }

    private fun recordFailure(at: Instant, summary: String, run: AuditRunContext, failure: String) {
        auditService?.recordRun(
            "SLACK_SCORING_SYNC_FAILED",
            AuditOutcome.FAILED,
            run,
            mapOf("failure" to failure),
        )
        statusRepository.recordFailed(at, summary)
        logger.warn("Slack scoring sync failed: {}", summary)
    }
}
