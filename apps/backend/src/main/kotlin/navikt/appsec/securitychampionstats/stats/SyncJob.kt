package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.slack.SlackService
import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.common.teams.GraphClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.Scheduled
import java.time.OffsetDateTime

@Component
class SyncJob(
    private val repo: PostgresRepository,
    private val catalog: TeamCatalog,
    private val slackService: SlackService,
    private val graphClient: GraphClient,
    @Value ("\${points.activityPoints}") private val activityPoints: Int,
) {
    @Scheduled(cron = "0 0 0 */3 * *")
    fun syncDatabase() {
        val members = repo.getAllMembers()
        val oneDayOld = OffsetDateTime.now().minusDays(1).toInstant()
        val catalogMembers = catalog.fetchMembersWithRole()

        catalogMembers.forEach {
            if (!members.any { member -> member.email == it.email}) {
                repo.addMember(it.fullName, it.navIdent, it.email ?: "unknown")
            }
        }

        members.forEach {
            if(it.inProgram && (it.lastUpdated == null || OffsetDateTime.parse(it.lastUpdated).toInstant().isBefore(oneDayOld))) {
                val activity = slackService.summarizeActivity(it.email)
                repo.addPoints(it.email, activity.totalMessages * activityPoints)
            }

            if (!catalogMembers.any { catalogMember -> catalogMember.email == it.email }) {
                repo.deleteMember(it.id)
            }
        }

        //TODO: Get people from teams and add them to guest if not SC, update points if they are SC
    }
}