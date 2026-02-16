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

    @GetMapping("/validate")
    fun getMe(): ResponseEntity<Me> {
        val authentication = SecurityContextHolder.getContext().authentication
        val email = authentication.name
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" }
        logger.info("Received request for /me from user with email: $email")

        val inProgram = repo.getMemberByEmail(email)?.inProgram

        return ResponseEntity(Me(email, isAdmin, inProgram ?: false), HttpStatus.OK)
    }

    @PostMapping("/join")
    fun applyMember(@RequestBody email: String): ResponseEntity<String> {
        val member = repo.getMemberByEmail(email)
        return if (member == null) {
            // Add member and maybe mark in db that they joined program but not SC? or send update in team catalog
            ResponseEntity(HttpStatus.OK)
        } else {
            // Update member in db that they joined program but not SC? or send update in team catalog
             ResponseEntity(HttpStatus.OK)
        }
    }
}