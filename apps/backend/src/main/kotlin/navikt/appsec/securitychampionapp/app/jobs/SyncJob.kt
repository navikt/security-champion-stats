package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.slack.ChannelMembershipService
import navikt.appsec.securitychampionapp.integrations.slack.dto.SecurityChampion
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component


private const val SYNC_JOB_LOCK_KEY = 1_001L

@Component
class SyncJob(
    private val jobLock: PostgresJobLock,
    private val repo: PostgresRepository,
    private val catalog: TeamCatalog,
    private val slackChannelMembershipService: ChannelMembershipService,
) {
    private val logger = LoggerFactory.getLogger(SyncJob::class.java)

    @Scheduled(cron = "0 0 12 */1 * *")
    fun syncDatabase() {
        jobLock.runWithLock(SYNC_JOB_LOCK_KEY, "syncDatabase") {
            val catalogMembers = catalog.fetchMembersWithRole()
            val queryResponse = repo.getAllMembers()
            val membersToAdd = mutableListOf<SecurityChampion>()

            if (!queryResponse.isOk || catalogMembers.isEmpty()) {
                logger.error("Failed to fetch members from database then sync data, with error: ${queryResponse.error}")
                return@runWithLock
            }
            val members = queryResponse.queryResult!!.toMutableList()

            catalogMembers.forEach { catalogMember ->
                if (members.none { it.email == catalogMember.email }) {
                    val response = repo.addMember(catalogMember.fullName, catalogMember.navIdent, catalogMember.email ?: "", catalogMember.teamName)
                    if (!response.isOk) {
                        logger.error("Failed to add member to database, with error: ${response.error}")
                    } else {
                        membersToAdd.add(
                            SecurityChampion(
                                email = catalogMember.email,
                                link = "",
                                teams = catalogMember.teamName,
                                imageUrl = "",
                                fullname = catalogMember.fullName
                            )
                        )
                    }
                }
                if (members.any {it.email == catalogMember.email && it.teams != catalogMember.teamName}) {
                    val response = repo.updateTeam(catalogMember.navIdent, catalogMember.teamName)
                    if (!response.isOk) {
                        logger.error("Failed to update member team in database, with error: ${response.error}")
                    }
                }
            }


            val membersToRemove = members.filter { member -> catalogMembers.none { it.email == member.email }}
            membersToRemove.forEach { member ->
                val response = repo.deleteMember(member.id)
                if (!response.isOk) {
                    logger.error("Failed to delete member from database, with error: ${response.error}")
                }
            }

            if (membersToAdd.isNotEmpty() || membersToRemove.isNotEmpty()) {
                val response = slackChannelMembershipService.updateUserGroupWithNewMembers()
                if (!response.isOk) {
                    logger.error("Failed to update slack user group with new members, with error: ${response.error}")
                    return@runWithLock
                }
            }

            if (membersToAdd.isNotEmpty()) {
                val response = slackChannelMembershipService.sendWelcomeMessage(membersToAdd)
                if (!response.isOk) {
                    logger.error("Failed to send out welcome message, with error: ${response.error}")
                    return@runWithLock
                }
            }
        }
    }
}