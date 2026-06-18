package navikt.appsec.securitychampionapp.app

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val RESET_POINTS_JOB_LOCK_KEY = 1_002L

@Component
class ResetPointsSyncJob(
    private val jobLock: PostgresJobLock,
    private val repo: PostgresRepository,
) {
    private val log = LoggerFactory.getLogger(ResetPointsSyncJob::class.java)

    @Scheduled(cron = "\${jobs.reset-points.cron}")
    fun resetAllPointsAndLevels() {
        jobLock.runWithLock(RESET_POINTS_JOB_LOCK_KEY, "resetAllPointsAndLevels") {
            val resetMembers = repo.resetAllPointsAndLevels()
            log.info("Reset points and levels for $resetMembers members")
        }
    }
}

