package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.api.dto.Me
import navikt.appsec.securitychampionapp.config.TeamCatalogRoleProperties
import navikt.appsec.securitychampionapp.app.api.dto.ProgramParticipantSummary
import navikt.appsec.securitychampionapp.app.api.dto.ProgramParticipantView
import navikt.appsec.securitychampionapp.app.events.EventCatalogService
import navikt.appsec.securitychampionapp.app.events.EventSignupService
import navikt.appsec.securitychampionapp.app.participation.EnrollmentOutcome
import navikt.appsec.securitychampionapp.app.participation.LeaveOutcome
import navikt.appsec.securitychampionapp.app.participation.ParticipantLifecycle
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(path = ["/api"])
@Tag(name = "Program")
class Controller(
    private val participantStore: ParticipantStore,
    private val participantLifecycle: ParticipantLifecycle,
    private val eventCatalogService: EventCatalogService,
    private val eventSignupService: EventSignupService,
    private val teamCatalogRoleProperties: TeamCatalogRoleProperties,
) {
    @GetMapping("/health")
    @Operation(summary = "Check API health")
    fun healthCheck(): String = "OK"

    @GetMapping("/members")
    @Operation(summary = "List active program members")
    fun getAllMembers(): ResponseEntity<List<ProgramParticipantSummary>> =
        ResponseEntity.ok(
            participantStore.findActiveParticipants().map {
                ProgramParticipantSummary(
                    id = it.id.toString(),
                    fullname = it.fullname,
                    teams = it.teams,
                )
            }
        )

    @GetMapping("/validate")
    @Operation(summary = "Get the current user's program access and membership status")
    fun getMe(): ResponseEntity<Me> {
        val principal = currentPrincipal()
        val isAdmin = requireNotNull(SecurityContextHolder.getContext().authentication).authorities
            .any { it.authority == "ROLE_$ADMIN_ROLE" }
        val participant = participantStore.findByNavNoEmail(principal.email)
        if (participant != null) {
            participantStore.updateAuthenticatedIdentity(principal.email, principal.navIdent, principal.email)
        }
        return ResponseEntity.ok(
            Me(
                username = principal.email,
                isAdmin = isAdmin,
                isParticipant = participant != null,
                isActive = participant?.status == ParticipationStatus.ACTIVE,
                teamCatalogRolesEnabled = teamCatalogRoleProperties.enabled,
            )
        )
    }

    @PostMapping("/enroll")
    @Operation(summary = "Enroll in the Security Champion program")
    @ApiResponse(responseCode = "201", description = "Enrollment created")
    @ApiResponse(responseCode = "200", description = "Already enrolled or rejoined")
    fun enroll(): ResponseEntity<String> {
        val principal = currentPrincipal()
        return when (participantLifecycle.enroll(principal.email, principal.navIdent, principal.email)) {
            EnrollmentOutcome.ENROLLED -> ResponseEntity.status(HttpStatus.CREATED).body("Enrolled in the program")
            EnrollmentOutcome.REJOINED -> ResponseEntity.ok("Rejoined the program")
            EnrollmentOutcome.ALREADY_ENROLLED -> ResponseEntity.ok("Already enrolled")
            EnrollmentOutcome.DEACTIVATED -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Participation is deactivated",
                "Program participation is deactivated",
            )
            EnrollmentOutcome.CONFLICT -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Participation changed",
                "Participation status changed during enrollment",
            )
        }
    }

    @PostMapping("/leave")
    @Operation(summary = "Leave the Security Champion program")
    @ApiResponse(responseCode = "204", description = "Membership ended")
    fun leave(): ResponseEntity<String> =
        when (participantLifecycle.leave(currentPrincipal().email)) {
            LeaveOutcome.LEFT, LeaveOutcome.ALREADY_LEFT -> ResponseEntity.noContent().build()
            LeaveOutcome.NOT_FOUND -> throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Participant not found",
                "Program participation does not exist",
            )
            LeaveOutcome.DEACTIVATED -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Participation is deactivated",
                "Program participation is deactivated",
            )
            LeaveOutcome.CONFLICT -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Participation changed",
                "Participation status changed during leave",
            )
        }

    @GetMapping("/membership")
    @Operation(summary = "Get the current user's program membership")
    fun fetchMembership(): ResponseEntity<ProgramParticipantView> {
        val principal = currentPrincipal()
        val participant = participantStore.findByNavNoEmail(principal.email)
            ?: throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Participant not found",
                "Program participation does not exist",
            )
        if (participantStore.updateAuthenticatedIdentity(principal.email, principal.navIdent, principal.email) == 0) {
            throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Participant not found",
                "Program participation no longer exists",
            )
        }
        return ResponseEntity.ok(
            ProgramParticipantView(
                id = participant.id.toString(),
                email = principal.email,
                fullname = participant.fullname,
                active = participant.status == ParticipationStatus.ACTIVE,
                joinedAt = participant.createdAt,
                teams = participant.teams,
                status = participant.status.name,
            )
        )
    }

    @GetMapping("/events")
    @Operation(summary = "List events available to the current user")
    fun fetchEvents(): ResponseEntity<List<Event>> =
        ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(eventSignupService.forParticipant(eventCatalogService.getAllEvents(), currentPrincipal().email))

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
