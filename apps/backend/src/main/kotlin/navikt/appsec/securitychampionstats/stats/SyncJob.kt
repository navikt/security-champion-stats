package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.slack.SlackService
import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.Scheduled
import java.time.OffsetDateTime
import java.time.Instant
import java.time.Clock
import java.time.temporal.ChronoUnit
import kotlin.time.Duration

@Component
class SyncJob(
    private val repo: PostgresRepository,
    private val catalog: TeamCatalog,
    private val slackService: SlackService,
    @Value($$"${points.message}") private val activityPoints: String,
    @Value($$"${slack.sc-channel-id}") private val scChannelId: String,
    @Value($$"${slack.appsec-channel-id}") private val appSecId: String,
) {
    @Scheduled(cron = "0 0 0 */5 * *")
    fun syncDatabase() {
        val members = repo.getAllMembers()
        val catalogMembers = catalog.fetchMembersWithRole()

        catalogMembers.forEach {
            if (!members.any { member -> member.email == it.email}) {
                repo.addMember(it.fullName, it.navIdent, it.email ?: "unknown")
            }
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