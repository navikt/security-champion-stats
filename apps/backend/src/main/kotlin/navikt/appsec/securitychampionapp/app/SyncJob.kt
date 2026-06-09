package navikt.appsec.securitychampionapp.app

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackService
import navikt.appsec.securitychampionapp.integrations.slack.dto.NewSecurityChampion
import navikt.appsec.securitychampionapp.integrations.slack.dto.RemovedSecurityChampion
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Component
class SyncJob(
    private val repo: PostgresRepository,
    private val catalog: TeamCatalog,
    private val slackService: SlackService,
    @Value($$"${points.message}") private val activityPoints: String,
    @Value($$"${slack.sc-channel-id}") private val scChannelId: String,
    @Value($$"${slack.appsec-channel-id}") private val appSecId: String,
) {
    private val teamUrl = "https://teamkatalog.nav.no/team/"

    @Scheduled(cron = "0 0 0 */2 * *")
    fun syncDatabase() {
        val members = repo.getAllMembers().toMutableList()
        val catalogMembers = catalog.fetchMembersWithRole()
        val slackListToAdd = mutableListOf<NewSecurityChampion>()

        catalogMembers.forEach {
            if (members.any { member -> member.email == it.email}) {
                members.remove(members.first { member -> member.email == it.email })
            } else {
                repo.addMember(it.fullName, it.navIdent, it.email ?: "unknown")
                slackListToAdd.add(NewSecurityChampion(
                    it.email,
                    it.teamName,
                    it.fullName
                ))
            }
        }
        slackService.addSecurityChampionsToSlack(champions = slackListToAdd)
        if (members.isNotEmpty()) {
            members.forEach { repo.deleteMember(it.email) }
            slackService.announceSecurityChampionsRemovedFromSlack(champions = members.map {
                RemovedSecurityChampion(
                    it.email,
                    "",
                    it.fullname
                )
            })
        }

        val membersInProgram = repo.getAllMembersInProgram()

        membersInProgram.forEach {
            if (isOlderThanTwoDays(it.lastUpdated)) {
                slackService.getUserActivitySummaryByEmail(it.email, listOf(scChannelId, appSecId))
                    .takeIf { summary -> summary.totalMessages > 0 }
                    ?.let { summary ->
                        val newPoints = it.points + (summary.totalMessages * activityPoints.toInt())
                        repo.addPoints(it.email, newPoints)
                    }
            }
        }
    }

    private fun isOlderThanTwoDays(lastUpdated: String?, clock: Clock = Clock.systemUTC()): Boolean {
        val instant = lastUpdated
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { parseTimestamptzToInstant(it) }
            ?: return true

        val threshold = Instant.now(clock).minus(2, ChronoUnit.DAYS)
        return instant.isBefore(threshold)
    }

    private fun parseTimestamptzToInstant(text: String): Instant {
        runCatching {
            return Instant.parse(text)
        }

        val isoLike = text.replace(' ', 'T')
        return OffsetDateTime.parse(isoLike).toInstant()
    }
}