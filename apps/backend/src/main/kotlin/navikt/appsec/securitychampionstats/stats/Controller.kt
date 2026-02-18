package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.stats.dto.Me
import navikt.appsec.securitychampionstats.stats.dto.Member
import navikt.appsec.securitychampionstats.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping(path = ["/api"])
class Controller(
    private val repo: PostgresRepository,
    private val catalog: TeamCatalog,
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)
    private val validate = Validate()

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

    @GetMapping("/validate")
    fun getMe(): ResponseEntity<Me> {
        val authentication = SecurityContextHolder.getContext().authentication
        val email = authentication.name
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" }
        val inProgram = repo.getMemberByEmail(email)?.inProgram

        return ResponseEntity(Me(email, isAdmin, inProgram ?: false), HttpStatus.OK)
    }

    @PostMapping("/join")
    fun applyMember(@RequestBody email: String): ResponseEntity<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        logger.info("User with email ${authentication.name} is attempting to join program with email $email")
        when {
            !validate.isValidEmail(email) -> {
                logger.warn("User with email ${authentication.name} attempted to join program with invalid email $email")
                return ResponseEntity(HttpStatus.BAD_REQUEST)
            }
            authentication.name != email -> {
                logger.warn("User with email ${authentication.name} attempted to join program with email $email")
                return ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
        }

        val member = repo.getMemberByEmail(email)
        return if (member == null) {
            logger.warn("User with email $email attempted to join program but is not a member")
            ResponseEntity(HttpStatus.OK)
        } else {
            repo.updateInProgram(email, true)
             ResponseEntity(HttpStatus.OK)
        }
    }

    @PostMapping("/leave")
    fun leaveProgram(@RequestBody email: String): ResponseEntity<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        when {
            !validate.isValidEmail(email) -> {
                logger.warn("User with email ${authentication.name} attempted to leave program with invalid email $email")
                return ResponseEntity(HttpStatus.BAD_REQUEST)
            }
            authentication.name != email && !authentication.authorities.any { it.authority == "ROLE_ADMIN" } -> {
                logger.warn("User with email ${authentication.name} attempted to leave program with email $email")
                return ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
        }

        val member = repo.getMemberByEmail(email)
        return if (member == null) {
            logger.warn("User with email $email attempted to leave program but is not a member")
            ResponseEntity(HttpStatus.OK)
        } else {
            repo.updateInProgram(email, false)
            ResponseEntity(HttpStatus.OK)
        }
    }
}