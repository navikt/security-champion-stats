package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.app.api.dto.AddMember
import navikt.appsec.securitychampionapp.app.api.dto.Points
import navikt.appsec.securitychampionapp.app.api.dto.SCdata
import navikt.appsec.securitychampionapp.integrations.slack.SlackService
import navikt.appsec.securitychampionapp.integrations.slack.dto.NewSecurityChampion
import navikt.appsec.securitychampionapp.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val POINTS_FOR_MEETING = 4

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val repo: PostgresRepository,
    private val slackService: SlackService
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)
    private val validate = Validate()

    @PostMapping("/member", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addMember(@RequestBody memberInfo: AddMember): ResponseEntity<Any>{
        if (!validate.isValidEmail(memberInfo.email) or !validate.isValidName(memberInfo.fullname)) {
            logger.warn("Attempt to add member failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication?.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        val id = UUID.randomUUID().toString()
        repo.addMember(memberInfo.fullname, id = id, memberInfo.email, emptyList())
        return ResponseEntity("User was created", HttpStatus.CREATED)
    }

    @DeleteMapping("/member/{email}")
    fun deleteMember(@PathVariable email: String): ResponseEntity<Any>{
        if (!validate.isValidEmail(email)) {
            logger.warn("Attempt to delete member failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication?.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        repo.deleteMember(email)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/points")
    fun addPoints(@RequestBody points: Points): ResponseEntity<Any>{
        if (!validate.isValidEmail(points.email) or !validate.isValidNumber(points.points.toString())) {
            logger.warn("Attempt to add points for user failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication?.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        repo.addPoints(points.email, points.points)
        return ResponseEntity("Points where added for user", HttpStatus.ACCEPTED)
    }

    @GetMapping("/dashboard/members")
    fun getAllMembers(): ResponseEntity<List<SCdata>> {
        // TODO: Add support for specific dates.
        return ResponseEntity.ok(repo.getSCAmountOverTime())
    }

    @PostMapping("/test/member/add/slack/{email}")
    fun addMemberToSlack(@PathVariable email: String): ResponseEntity<Any> {
        var member = repo.getMemberByEmail(email)
        if (member == null) {
            repo.addMember("Paulius Deveika", id = UUID.randomUUID().toString(), email, listOf("appsec"))
            member = repo.getMemberByEmail(email)
        }
        slackService.addSecurityChampionsToSlack(
            "C0314EZ719S",
            listOf(
                NewSecurityChampion(
                    email = member!!.email,
                    teamNames = member.teams,
                    fullName = member.fullname
                )
            )
        )

        return ResponseEntity.ok().build()
    }

    @PostMapping("/member/attended/{email}")
    fun validateMemberAttendingMeeting(@PathVariable email: String): ResponseEntity<Any> {
        val member = repo.getMemberByEmail(email)
        val updatedPoints = (member?.points ?: 0) + POINTS_FOR_MEETING
        repo.addPoints(email, updatedPoints)
        return ResponseEntity.ok().build()
    }
}