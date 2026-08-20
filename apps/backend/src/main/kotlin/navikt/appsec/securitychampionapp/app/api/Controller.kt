package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.app.api.dto.Me
import navikt.appsec.securitychampionapp.app.api.dto.Member
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.math.log


@RestController
@RequestMapping(path = ["/api"])
class Controller(
    private val repo: PostgresRepository,
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)

    @GetMapping("/health")
    fun healthCheck(): String = "OK"

    //TODO: Potentially delete this... might not be needed
    //TODO: Reformat to use id instead of emails
    @GetMapping("/members")
    fun getAllMembers(): ResponseEntity<List<Member>> {
        val queryResponse = repo.getAllMembers()

        if (!queryResponse.isOk) {
            logger.warn("Failed to fetch all member from database due to error: ${queryResponse.error}")
            return ResponseEntity(emptyList(), HttpStatus.INTERNAL_SERVER_ERROR)
        }

        val response = queryResponse.queryResult!!
            .filter { it.inProgram }
            .map { members ->
                Member(
                    id = members.id,
                    fullname = members.fullname,
                    points = members.points,
                    email = members.email,
                    level = members.level,
                    inGame = members.inProgram,
                    joinedAt = members.createdAt
                )
            }
        return ResponseEntity(response, HttpStatus.OK)
    }

    @GetMapping("/validate")
    fun getMe(): ResponseEntity<Me> {

        logger.info("Validating user")
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_$ADMIN_ROLE" }
        val queryResponse = repo.getMemberByEmail(email)

        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            logger.info("New potential new user")
            return ResponseEntity(Me(email, isAdmin, isSecChamp = false, inGame = false), HttpStatus.OK)
        }

        val inProgram = queryResponse.queryResult.firstOrNull()?.inProgram ?: false
        logger.info("User data: ${queryResponse.queryResult.firstOrNull()}")
        return ResponseEntity(Me(email, isAdmin, isSecChamp = true, inProgram), HttpStatus.OK)
    }

    @PostMapping("/join")
    fun applyMember(): ResponseEntity<String> {
        return updateUserInProgramStatus(true)
    }

    @PostMapping("/leave")
    fun leaveProgram(): ResponseEntity<String> {
        return updateUserInProgramStatus(false)
    }

    @GetMapping("/membership")
    fun fetchMembership(): ResponseEntity<Member> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val id = principal.navIdent

        val queryResponse = repo.fetchMember(id)
        if (queryResponse == null || !queryResponse.isOk) {
            logger.warn("Failed to fetch member from database due to error: ${queryResponse?.error}")
            return ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
        }

        logger.info("Fetched member: ${queryResponse.queryResult}")
        return ResponseEntity.status(HttpStatus.OK).body(
            Member(
                id = queryResponse.queryResult!!.first().id,
                email = queryResponse.queryResult.first().email,
                fullname = queryResponse.queryResult.first().fullname,
                points = queryResponse.queryResult.first().points,
                level = queryResponse.queryResult.first().level,
                inGame = queryResponse.queryResult.first().inProgram,
                joinedAt = queryResponse.queryResult.first().createdAt
            )
        )
    }

    private fun updateUserInProgramStatus(status: Boolean): ResponseEntity<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email
        val queryResponse = repo.getMemberByEmail(email)
        if (!queryResponse.isOk) {
            return returnInternalError("Failed to find/fetch member due to error: ${queryResponse.error}")
        }
        val id = queryResponse.queryResult!!.firstOrNull()?.id ?: ""
        val updateResponse = repo.updateInProgram(id, status)

        if (!updateResponse.isOk) {
            return returnInternalError("Failed to update member inProgram status due to error: ${updateResponse.error}")
        }

        return ResponseEntity(HttpStatus.OK)
    }


    private fun returnInternalError(error: String): ResponseEntity<String> {
        logger.error("Internal error: $error")
        return ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
