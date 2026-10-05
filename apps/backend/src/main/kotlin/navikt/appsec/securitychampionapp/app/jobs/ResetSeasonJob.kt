package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val RESET_SEASON_JOB_LOCK_KEY = 1_002L

@Component
class ResetSeasonJob(
    private val jobLock: PostgresJobLock,
    private val scoringService: ScoringService,
) {
    private val logger = LoggerFactory.getLogger(ResetSeasonJob::class.java)

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Oslo")
    fun resetSeasonIfDue() {
        jobLock.runWithLock(RESET_SEASON_JOB_LOCK_KEY, "resetSeasonIfDue") {
            if (scoringService.resetIfDue()) {
                logger.info("Started a new program season")
            }
        }
    }
}
