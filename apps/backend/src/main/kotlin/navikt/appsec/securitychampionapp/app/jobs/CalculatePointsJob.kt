package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.slack.ActivityService
import navikt.appsec.securitychampionapp.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component


private const val CALCULATE_POINTS_JOB_LOCK_KEY = 1_003L

@Component
class CalculatePointsJob(
    private val jobLock: PostgresJobLock,
    private val slackActivityService: ActivityService,
    private val repo: PostgresRepository,
    private val validate: Validate,
) {

    private val logger = LoggerFactory.getLogger(CalculatePointsJob::class.java)

    @Scheduled(cron = "0 0 13 */1 * *")
    fun calculatePoints() {
        jobLock.runWithLock(CALCULATE_POINTS_JOB_LOCK_KEY, "calculatePoints") {
            val queryResponse = repo.getAllMembersInProgram()

            if (!queryResponse.isOk || queryResponse.queryResult.isNullOrEmpty()) {
                logger.error("Failed to fetch all members in program from database, with error: ${queryResponse.error}")
                return@runWithLock
            }

            val emails = queryResponse.queryResult.map { it.email }
            val slackResponse = slackActivityService.calculateActivityForMembers(emails)

            if (!slackResponse.isOk) {
                logger.error("Failed to calculate activity for members, with error: ${slackResponse.error}")
                return@runWithLock
            }

            slackResponse.slackActivitySummaries.forEach { summary ->
                val user = queryResponse.queryResult.first { it.email == summary.email }
                val updatedLevel = validate.calculateLevel(summary.totalPoints + user.points)
                val totalPoints = user.points + summary.totalPoints

                val updateResponse = repo.addPoints(user.id, totalPoints, updatedLevel)
                if (!updateResponse.isOk) {
                    logger.error("Failed to update points for member ${user.id}, with error: ${updateResponse.error}")
                }
            }
        }
    }
}
