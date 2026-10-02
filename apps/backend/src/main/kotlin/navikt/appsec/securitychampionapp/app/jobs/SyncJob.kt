package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val SYNC_JOB_LOCK_KEY = 1_001L

@Component
class SyncJob(
    private val jobLock: PostgresJobLock,
    private val repo: ProgramParticipantRepository,
    private val catalog: TeamCatalog,
) {
    private val logger = LoggerFactory.getLogger(SyncJob::class.java)

    @Scheduled(cron = "0 0 12 */1 * *")
    fun syncDatabase() {
        jobLock.runWithLock(SYNC_JOB_LOCK_KEY, "syncDatabase") {
            val catalogMembers = catalog.fetchAllMembersWithTeamData()
            if (catalogMembers.isEmpty()) {
                logger.warn("Skipping participant profile sync because Teamkatalogen returned no members")
                return@runWithLock
            }

            val participants = repo.findAllParticipants()
            if (!participants.isOk) {
                logger.error("Failed to fetch program participants: ${participants.error}")
                return@runWithLock
            }

            catalogMembers.forEach { catalogMember ->
                val hasParticipant = participants.queryResult.any {
                    it.navIdent == catalogMember.navIdent && it.email == catalogMember.email
                }
                if (hasParticipant) {
                    val response = repo.updateProfile(
                        navIdent = catalogMember.navIdent,
                        email = catalogMember.email,
                        fullname = catalogMember.fullName,
                        teams = catalogMember.teamName,
                    )
                    if (!response.isOk) {
                        logger.error("Failed to update participant profile: ${response.error}")
                    }
                }
            }
        }
    }
}
