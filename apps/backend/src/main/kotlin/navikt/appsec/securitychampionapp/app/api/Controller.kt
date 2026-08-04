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
import kotlin.math.log


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

    @GetMapping("/members")
    fun getAllMembers(): ResponseEntity<List<Member>> {
        val members = repo.getAllMembers()

        if (!members.isOk) {
            logger.warn("Failed to fetch all member from database due to error: ${members.error}")
            return ResponseEntity(emptyList(), HttpStatus.INTERNAL_SERVER_ERROR)
        }

        val response = members.queryResult!!
            .filter { it.inProgram }
            .map { members ->
                Member(
                    id = members.id,
                    fullname = members.fullname,
                    points = members.points,
                    email = members.email,
                    level = members.level
                )
            }
        return ResponseEntity(response, HttpStatus.OK)
    }

    @GetMapping("/validate")
    fun getMe(): ResponseEntity<Me> {
        val authentication = SecurityContextHolder.getContext().authentication
        val email = authentication?.name.orEmpty()
        val isAdmin = authentication?.authorities?.any { it.authority == "ROLE_$ADMIN_ROLE" } ?: false
        val queryResponse = repo.getMemberByEmail(email)

        if (!queryResponse.isOk) {
            logger.warn("Failed to fetch member from database due to error: ${queryResponse.error}")
            return ResponseEntity(Me(email, isAdmin, false), HttpStatus.INTERNAL_SERVER_ERROR)
        }
        val inProgram = queryResponse.queryResult!!.firstOrNull()?.inProgram ?: false
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
