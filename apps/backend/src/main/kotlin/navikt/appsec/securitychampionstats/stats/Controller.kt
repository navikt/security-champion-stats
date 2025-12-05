package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.integration.postgres.PostgresRepository
import navikt.appsec.securitychampionstats.integration.slack.SlackService
import navikt.appsec.securitychampionstats.integration.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.integration.teams.GraphClient
import navikt.appsec.securitychampionstats.integration.zoom.ZoomMeetingService
import navikt.appsec.securitychampionstats.stats.dto.DeleteMember
import navikt.appsec.securitychampionstats.stats.dto.Member
import navikt.appsec.securitychampionstats.stats.dto.MemberInfo
import navikt.appsec.securitychampionstats.stats.dto.Points
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    private val zoomService: ZoomMeetingService,
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

    @PostMapping("/member")
    fun addMember(@RequestBody memberInfo: MemberInfo): ResponseEntity<Any>{
        repo.addMember(memberInfo.fullname, memberInfo.id, memberInfo.email)
        return ResponseEntity("User was created", HttpStatus.CREATED)
    }

    @DeleteMapping("/member")
    fun deleteMember(@RequestBody member: DeleteMember): ResponseEntity<Any>{
        repo.deleteMember(member.id)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/points")
    fun addPoints(@RequestBody points: Points): ResponseEntity<Any>{
        repo.addPoints(points.id, points.points)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @GetMapping("/slackTest")
    fun slackTest() {
        val member = repo.getMember(testId)
        if (member == null) {
            logger.error("Member with id $testId not found in db")
            return
        }
        val activity = slackService.summarizeActivity(testEmail)
        logger.info("Member from db: $member")
        logger.info("Slack activity: $activity")
        val points = activity.totalMessages * 10
        repo.addPoints(member.id, points)
    }

    @GetMapping("/zoomTest/{meetingId}")
    fun zoomTest(@PathVariable meetingId: String): ResponseEntity<String> {
        // temp not available
        val participants = zoomService.getLiveParticipants(meetingId)
        logger.info("Fetched $participants participants from zoom meeting $meetingId")
        if (participants.participants.isNullOrEmpty()) {
            logger.info("No participants found in zoom meeting")
            return ResponseEntity("No participants found in zoom meeting", HttpStatus.OK)
        }
        logger.info("Zoom participants: $participants")
        val member = participants.participants.filter {
            it.email == testEmail
        }

        return if (!member.isEmpty()) {
            logger.info("Found test member in zoom meeting ${member.first().userName}")
            repo.addPoints(testId, 50)
            ResponseEntity("Test member found in zoom meeting", HttpStatus.OK)
        } else {
            logger.info("Failed to fetch test member from zoom")
            ResponseEntity("Test member not found in zoom meeting", HttpStatus.OK)
        }
    }

    @GetMapping("/Teams")
    fun getTeamsMeetingAttendance(@RequestParam meetingId: String, @RequestParam meetingDate: String): ResponseEntity<String> {
        val offsetTime = OffsetDateTime.parse(meetingDate)
        val attendanceReport = graphClient.getListAttendanceReport(meetingId, offsetTime)

        if (attendanceReport.isEmpty()) {
            logger.info("No attendance records found for meetingId: $meetingId")
            return ResponseEntity("No attendance records found", HttpStatus.OK)
        }
        attendanceReport.forEach {
            if(!it.emailAddress.isNullOrEmpty()) {
                val member = repo.getMember("", it.emailAddress)
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