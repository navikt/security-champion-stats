package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.app.api.dto.ActivityClaim
import navikt.appsec.securitychampionapp.app.api.dto.DisplayNameUpdate
import navikt.appsec.securitychampionapp.app.api.dto.InviteRequest
import navikt.appsec.securitychampionapp.app.api.dto.InviteResponse
import navikt.appsec.securitychampionapp.app.api.dto.Me
import navikt.appsec.securitychampionapp.app.api.dto.Member
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import navikt.appsec.securitychampionapp.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val APPSEC_TEAM_EMAIL = "appsec@nav.no"
private const val MIN_ACTIVITY_CLAIM = 1
private const val MAX_ACTIVITY_CLAIM = 5

@RestController
@RequestMapping(path = ["/api"])
class Controller(
    private val repo: PostgresRepository,
    private val validate: Validate,
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)

    @GetMapping("/health")
    fun healthCheck(): String = "OK"

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

    @PostMapping("/joinGame")
    fun applyMember(): ResponseEntity<String> {
        return updateUserInProgramStatus(true)
    }

    @PostMapping("/leaveGame")
    fun leaveProgram(): ResponseEntity<String> {
        return updateUserInProgramStatus(false)
    }

    @GetMapping("/membership")
    fun fetchMembership(@RequestParam(required = false) id: String?): ResponseEntity<Member> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val resolvedId = id ?: principal.navIdent

        val queryResponse = repo.fetchMember(resolvedId)
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

    @PostMapping("/invite", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun inviteColleague(@RequestBody invite: InviteRequest): ResponseEntity<InviteResponse> {
        if (invite.requesterEmail != APPSEC_TEAM_EMAIL) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(InviteResponse("forbidden"))
        }

        val id = UUID.randomUUID().toString()
        repo.addMember(invite.fullName, id = id, invite.email, emptyList())

        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val notice = if (principal.email != APPSEC_TEAM_EMAIL) "FLAG{client_side_authz_bypass}" else null
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse("created", notice))
    }

    @PostMapping("/activity/claim", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun claimActivityPoints(@RequestBody claim: ActivityClaim): ResponseEntity<InviteResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email

        val queryResponse = repo.getMemberByEmail(email)
        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(InviteResponse("not_found"))
        }

        val current = queryResponse.queryResult.first()
        val newTotal = current.points + claim.amount
        val level = validate.calculateLevel(newTotal)
        repo.addPoints(current.id, claim.amount, level)

        val withinIntendedRange = claim.amount in MIN_ACTIVITY_CLAIM..MAX_ACTIVITY_CLAIM
        val notice = if (!withinIntendedRange) "FLAG{unvalidated_business_logic_bypass}" else null
        return ResponseEntity.ok(InviteResponse("claimed", notice))
    }

    @PostMapping("/profile/displayname", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateDisplayName(@RequestBody body: DisplayNameUpdate): ResponseEntity<InviteResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as AppPrincipal
        val email = principal.email

        val queryResponse = repo.getMemberByEmail(email)
        if (!queryResponse.isOk || queryResponse.queryResult!!.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(InviteResponse("not_found"))
        }

        val id = queryResponse.queryResult.first().id
        repo.updateFullname(id, body.displayName)
        return ResponseEntity.ok(InviteResponse("updated"))
    }

    @GetMapping("/xss/proof")
    fun xssProof(): ResponseEntity<InviteResponse> {
        return ResponseEntity.ok(InviteResponse("proof", "FLAG{stored_xss_client_side_filter_bypass}"))
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
