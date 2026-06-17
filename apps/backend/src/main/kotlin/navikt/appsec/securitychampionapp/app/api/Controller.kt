package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.app.api.dto.Me
import navikt.appsec.securitychampionapp.app.api.dto.Member
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    @Value($$"${spring.profiles.active}") val activeProfiles: String
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)
    private val validate = Validate()

    @GetMapping("/health")
    fun healthCheck(): String = "OK"

    // TODO: Might need to alter the Member sql
    @GetMapping("/members")
    fun getAllMembers(): ResponseEntity<List<Member>> {
        logger.info("Request to fetch all members was made")
        val members = repo.getAllMembersInProgram()
        return if (members.isEmpty() && activeProfiles != "local") {
            catalog.fetchMembersWithRole().forEach {
                repo.addMember(
                    fullname = it.fullName,
                    id = it.navIdent,
                    email = it.email,
                    teams = it.teamName
                )
            }
            ResponseEntity(repo.getAllMembersInProgram(), HttpStatus.OK)
        } else {
            ResponseEntity(members, HttpStatus.OK)
        }
    }

    @GetMapping("/validate")
    fun getMe(): ResponseEntity<Me> {
        val authentication = SecurityContextHolder.getContext().authentication
        val email = authentication?.name.orEmpty()
        val isAdmin = authentication?.authorities?.any { it.authority == "ROLE_$ADMIN_ROLE" } ?: false
        val inProgram = repo.getMemberByEmail(email)?.inProgram ?: false
        return ResponseEntity(Me(email, isAdmin, inProgram), HttpStatus.OK)
    }

    @PostMapping("/join")
    fun applyMember(@RequestBody email: String): ResponseEntity<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        when {
            !validate.isValidEmail(email) -> {
                logger.warn("User with email ${authentication?.name} attempted to join program with invalid email $email")
                return ResponseEntity(HttpStatus.BAD_REQUEST)
            }
            authentication?.name != email -> {
                logger.warn("User with email ${authentication?.name} attempted to join program with email $email")
                return ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
        }

        val member = repo.getMemberByEmail(email)
        return if (member == null) {
            logger.warn("User with email $email attempted to join program but is not a member")
            ResponseEntity(HttpStatus.BAD_REQUEST)
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
                logger.warn("User with email ${authentication?.name} attempted to leave program with invalid email $email")
                return ResponseEntity(HttpStatus.BAD_REQUEST)
            }
            authentication?.name != email -> {
                logger.warn("User with email ${authentication?.name} attempted to leave program with email $email")
                return ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
        }

        val member = repo.getMemberByEmail(email)
        return if (member == null) {
            logger.warn("User with email $email attempted to leave program but is not a member")
            ResponseEntity(HttpStatus.BAD_REQUEST)
        } else {
            repo.updateInProgram(email, false)
            ResponseEntity(HttpStatus.OK)
        }
    }
}
