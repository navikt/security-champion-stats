package navikt.appsec.securitychampionapp.app

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ResetPointsSyncJob(
    private val repo: PostgresRepository,
) {
    private val log = LoggerFactory.getLogger(ResetPointsSyncJob::class.java)

    @Scheduled(cron = "\${jobs.reset-points.cron}")
    fun resetAllPointsAndLevels() {
        val resetMembers = repo.resetAllPointsAndLevels()
        log.info("Reset points and levels for $resetMembers members")
    }
}
