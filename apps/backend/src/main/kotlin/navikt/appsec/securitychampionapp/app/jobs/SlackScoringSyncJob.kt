package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.scoring.SlackScoringService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private const val SLACK_SCORING_JOB_LOCK_KEY = 1_002L

@Component
class SlackScoringSyncJob(
    private val jobLock: PostgresJobLock,
    private val slackScoringService: SlackScoringService,
    @Value($$"${slack.sc-channel-id}") private val channelId: String,
) {
    private val logger = LoggerFactory.getLogger(SlackScoringSyncJob::class.java)

    @Scheduled(cron = "0 0 */6 * * *")
    fun syncSlackScoring() {
        jobLock.runWithLock(SLACK_SCORING_JOB_LOCK_KEY, "syncSlackScoring") {
            val summary = slackScoringService.sync(channelId, Instant.now())
            logger.info(
                "Slack scoring sync completed: scanned={}, awarded={}, duplicates={}, unmapped={}",
                summary.messagesScanned,
                summary.creditsAwarded,
                summary.duplicateCredits,
                summary.unmappedAuthors,
            )
        }
    }
}
