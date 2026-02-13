package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.slack.SlackService
import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.common.teams.GraphClient
import navikt.appsec.securitychampionstats.stats.dto.Me
import navikt.appsec.securitychampionstats.stats.dto.Member
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime


@RestController
@RequestMapping(path = ["/api"])
class Controller(
    private val repo: PostgresRepository,
    private val catalog: TeamCatalog,
    private val slackService: SlackService,
    private val graphClient: GraphClient,
    @Value("\${testData.testEmail}") private val testEmail: String,
    @Value("\${testData.testUserId}") private val testId: String
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)

    @GetMapping("/health")
    fun healthCheck(): String = "OK"

    @GetMapping("/members")
    fun getAllMembers(): ResponseEntity<List<Member>> {
        val members = repo.getAllMembers()

        return if (members.isEmpty()) {
            val members = catalog.fetchMembersWithRole()
            repo.addMembers(members)
            ResponseEntity(repo.getAllMembers(), HttpStatus.OK)
        } else {
            ResponseEntity(members, HttpStatus.OK)
        }
    }

    fun slackTest(): ResponseEntity<Any> {
        val member = repo.getMember(testId)
        if (member == null) {
            logger.error("Member with id $testId not found in db")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        val activity = slackService.summarizeActivity(testEmail)
        if (activity.totalMessages == 0) {
            logger.info("No messages found for user with email $testEmail")
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
            logger.info("No attendance records found for meetingId: $meetingId")
            return ResponseEntity("No attendance records found", HttpStatus.OK)
        }
        attendanceReport.forEach {
            if(!it.emailAddress.isNullOrEmpty()) {
                val member = repo.getMember(it.emailAddress)
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