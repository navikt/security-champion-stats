package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.slack.SlackService
import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.common.teams.GraphClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.RequestParam
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

    fun slackTest(): ResponseEntity<Any> {
        val testId = "dsfdfsdfsd"
        val testEmail = ""
        val member = repo.getMemberByEmail(testId)
        if (member == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        val activity = slackService.summarizeActivity(testEmail)
        if (activity.totalMessages == 0) {
            return ResponseEntity.status(HttpStatus.OK).build()
        }
        val points = activity.totalMessages * 10
        val result = repo.addPoints(member.email, points)

        return if (result == 1) {
            ResponseEntity.status(HttpStatus.OK).build()
        } else {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    fun getTeamsMeetingAttendance(@RequestParam meetingId: String, @RequestParam meetingDate: String): ResponseEntity<String> {
        // TODO: wait for getting tokens before using endpoint
        val offsetTime = OffsetDateTime.parse(meetingDate)
        val attendanceReport = graphClient.getListAttendanceReport(meetingId, offsetTime)

        if (attendanceReport.isEmpty()) {
            return ResponseEntity("No attendance records found", HttpStatus.OK)
        }
        attendanceReport.forEach {
            if(!it.emailAddress.isNullOrEmpty()) {
                val member = repo.getMemberByEmail(it.emailAddress)
                if (member != null && member.email.isEmpty()) {
                    // TODO: add a new table for activity monitoring
                } else if (member != null) {
                    // TODO: add a new table for activity monitoring
                    repo.addPoints(it.emailAddress, 10)
                }
            }
        }
        return ResponseEntity(HttpStatus.OK)
    }
}