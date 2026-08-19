package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.app.api.dto.AddMember
import navikt.appsec.securitychampionapp.app.api.dto.Points
import navikt.appsec.securitychampionapp.app.api.dto.SCdata
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

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val repo: PostgresRepository,
    private val validate: Validate,
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)

    @PostMapping("/member", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addMember(@RequestBody memberInfo: AddMember): ResponseEntity<Any>{
        if (!validate.isValidEmail(memberInfo.email) or !validate.isValidName(memberInfo.fullName)) {
            logger.warn("Attempt to add member failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication?.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        val id = UUID.randomUUID().toString()
        repo.addMember(memberInfo.fullName, id = id, memberInfo.email, emptyList())
        return ResponseEntity("User was created", HttpStatus.CREATED)
    }

    @DeleteMapping("/member/{id}")
    fun deleteMember(@PathVariable id: String): ResponseEntity<Any>{
        repo.deleteMember(id)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/points")
    fun addPoints(@RequestBody points: Points): ResponseEntity<Any>{
        if (!validate.isValidEmail(points.email) or !validate.isValidNumber(points.points.toString())) {
            logger.warn("Attempt to add points for user failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication?.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        val user = repo.getMemberByEmail(points.email)
        if (!user.isOk) {
            logger.warn("Failed to find user then updating the points")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        val newAmount = points.points + user.queryResult!!.first().points
        val level = validate.calculateLevel(newAmount)
        repo.addPoints(points.email, newAmount, level)
        return ResponseEntity("Points where added for user", HttpStatus.ACCEPTED)
    }

    @GetMapping("/dashboard/members")
    fun getAllMembers(): ResponseEntity<List<SCdata>> {
        return ResponseEntity.ok(repo.getSCAmountOverTime())
    }

    @PostMapping("/test/member/add/slack/{email}")
    fun addMemberToSlack(@PathVariable email: String): ResponseEntity<Any> {
        return ResponseEntity.ok().build()
    }

    @PostMapping("/member/attended/{email}")
    fun validateMemberAttendingMeeting(@PathVariable email: String): ResponseEntity<Any> {
        return ResponseEntity.ok().build()
    }
}