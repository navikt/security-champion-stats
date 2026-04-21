package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.stats.dto.DeleteMember
import navikt.appsec.securitychampionstats.stats.dto.Member
import navikt.appsec.securitychampionstats.stats.dto.MemberInfo
import navikt.appsec.securitychampionstats.stats.dto.Points
import navikt.appsec.securitychampionstats.stats.dto.SCdata
import navikt.appsec.securitychampionstats.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val repo: PostgresRepository,
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)
    private val validate = Validate()
    @PostMapping("/member")
    fun addMember(@RequestBody memberInfo: MemberInfo): ResponseEntity<Any>{
        logger.info("Request to add member by user" +
                " ${SecurityContextHolder.getContext().authentication.name}")
        if (!validate.isValidEmail(memberInfo.email) or !validate.isValidName(memberInfo.fullname)) {
            logger.warn("Attempt to add member failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        val id = UUID.randomUUID().toString()
        repo.addMember(memberInfo.fullname, id = id, memberInfo.email)
        return ResponseEntity("User was created", HttpStatus.CREATED)
    }

    @DeleteMapping("/member")
    fun deleteMember(@RequestBody member: DeleteMember): ResponseEntity<Any>{
        logger.info("Request to delete member was made by user " +
                "${SecurityContextHolder.getContext().authentication.name}")
        if (!validate.isValidEmail(member.email)) {
            logger.warn("Attempt to delete member failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        repo.deleteMember(member.email)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/points")
    fun addPoints(@RequestBody points: Points): ResponseEntity<Any>{
        logger.info("Request to add points was made by user " +
                "${SecurityContextHolder.getContext().authentication.name}")
        if (!validate.isValidEmail(points.email) or !validate.isValidNumber(points.points.toString())) {
            logger.warn("Attempt to add points for user failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication.name}")
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }
        repo.addPoints(points.email, points.points)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @GetMapping("/dashboard/members")
    fun getAllMembers(): ResponseEntity<List<SCdata>> {
        // TODO: Add support for specific dates.
        return ResponseEntity.ok(repo.getSCAmountOverTime())
    }
}