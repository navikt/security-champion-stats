package navikt.appsec.securitychampionapp.app.jobs

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

private const val SLACK_SCORING_JOB_LOCK_KEY = 1_002L

@Component
class SlackScoringSyncJob(
    private val jobLock: PostgresJobLock,
    private val syncTrigger: ScoringSyncTrigger,
    private val slackScoringService: SlackScoringService,
    private val statusRepository: SlackScoringStatusRepository,
    @Value($$"${slack.sc-channel-id}") private val channelId: String,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(SlackScoringSyncJob::class.java)

    @Scheduled(cron = $$"${slack.scoring.cron:0 0 */6 * * *}")
    fun syncSlackScoring() {
        jobLock.runWithLock(SLACK_SCORING_JOB_LOCK_KEY, "syncSlackScoring", ::runSync)
    }

    fun triggerManualSync(): SyncTriggerResult =
        syncTrigger.trigger(SLACK_SCORING_JOB_LOCK_KEY, "syncSlackScoring", ::runSync)

    private fun runSync() {
        val attemptAt = clock.instant()
        statusRepository.recordStarted(attemptAt)
        try {
            val summary = slackScoringService.sync(channelId, attemptAt)
            statusRepository.recordSucceeded(clock.instant(), summary)
            logger.info(
                "Slack scoring sync completed: scanned={}, awarded={}, duplicates={}, unmapped={}",
                summary.messagesScanned,
                summary.creditsAwarded,
                summary.duplicateCredits,
                summary.unmappedAuthors,
            )
        } catch (_: SlackIntegrationException) {
            recordFailure(
                clock.instant(),
                "Slack activity could not be synchronized; check API access and channel configuration",
            )
        } catch (_: IllegalStateException) {
            recordFailure(clock.instant(), "Slack scoring configuration is incomplete")
        } catch (_: DataAccessException) {
            recordFailure(clock.instant(), "Slack scoring could not persist sync results")
        }
    }

    private fun recordFailure(at: Instant, summary: String) {
        statusRepository.recordFailed(at, summary)
        logger.warn("Slack scoring sync failed: {}", summary)
    }
}
