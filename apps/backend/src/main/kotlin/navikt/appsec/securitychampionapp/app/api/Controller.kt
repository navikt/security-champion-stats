package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.api.dto.Me
import navikt.appsec.securitychampionapp.app.api.dto.ProgramParticipantSummary
import navikt.appsec.securitychampionapp.app.api.dto.ProgramParticipantView
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api"])
class Controller(
    private val participantRepository: ProgramParticipantRepository,
    private val eventRepository: EventRepository,
    private val teamCatalog: TeamCatalog,
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)

    @GetMapping("/health")
    fun healthCheck(): String = "OK"

    @GetMapping("/members")
    fun getAllMembers(): ResponseEntity<List<ProgramParticipantSummary>> {
        val response = participantRepository.findActiveParticipants()
        if (!response.isOk) {
            logger.warn("Failed to fetch program participants: ${response.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        return ResponseEntity.ok(
            response.queryResult.map {
                ProgramParticipantSummary(
                    id = it.id,
                    fullname = it.fullname,
                    teams = it.teams,
                )
            }
        )
    }

    @GetMapping("/validate")
    fun getMe(): ResponseEntity<Me> {
        val principal = currentPrincipal()
        val isAdmin = requireNotNull(SecurityContextHolder.getContext().authentication).authorities
            .any { it.authority == "ROLE_$ADMIN_ROLE" }
        val queryResponse = participantRepository.findByNavNoEmail(principal.navNoEmail)
        if (!queryResponse.isOk) {
            logger.warn("Failed to validate program participant: ${queryResponse.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        val participant = queryResponse.queryResult.firstOrNull()
        if (participant != null) {
            val updateResponse = participantRepository.updateAuthenticatedIdentity(
                navNoEmail = principal.navNoEmail,
                navIdent = principal.navIdent,
                email = principal.email,
            )
            if (!updateResponse.isOk) {
                logger.warn("Failed to update participant identity: ${updateResponse.error}")
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }
        }

        return ResponseEntity.ok(
            Me(
                username = principal.email,
                isAdmin = isAdmin,
                isParticipant = participant != null,
                isActive = participant?.status == "ACTIVE",
            )
        )
    }

    @PostMapping("/enroll")
    fun enroll(): ResponseEntity<String> {
        val principal = currentPrincipal()
        val existingResponse = participantRepository.findByNavNoEmail(principal.navNoEmail)
        if (!existingResponse.isOk) {
            logger.warn("Failed to find program participant: ${existingResponse.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        val existingParticipant = existingResponse.queryResult.firstOrNull()
        if (existingParticipant != null) {
            if (existingParticipant.status == "DEACTIVATED") {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Program participation is deactivated")
            }
            return ResponseEntity.ok("Already enrolled")
        }

        val profile = teamCatalog.fetchAllMembersWithTeamData().firstOrNull {
            it.navIdent == principal.navIdent && it.email == principal.email
        }
        val enrollmentResponse = participantRepository.enroll(
            navNoEmail = principal.navNoEmail,
            navIdent = principal.navIdent,
            email = principal.email,
            fullname = profile?.fullName.orEmpty(),
            teams = profile?.teamName.orEmpty(),
        )
        if (!enrollmentResponse.isOk) {
            logger.error("Failed to enroll program participant: ${enrollmentResponse.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        val identityUpdateResponse = participantRepository.updateAuthenticatedIdentity(
            navNoEmail = principal.navNoEmail,
            navIdent = principal.navIdent,
            email = principal.email,
        )
        if (!identityUpdateResponse.isOk) {
            logger.error("Failed to update enrolled participant identity: ${identityUpdateResponse.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        return ResponseEntity.status(HttpStatus.CREATED).body("Enrolled in the program")
    }

    @GetMapping("/membership")
    fun fetchMembership(): ResponseEntity<ProgramParticipantView> {
        val principal = currentPrincipal()
        val queryResponse = participantRepository.findByNavNoEmail(principal.navNoEmail)
        if (!queryResponse.isOk) {
            logger.warn("Failed to fetch program participant: ${queryResponse.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        val participant = queryResponse.queryResult.firstOrNull()
            ?: return ResponseEntity.notFound().build()
        val updateResponse = participantRepository.updateAuthenticatedIdentity(
            navNoEmail = principal.navNoEmail,
            navIdent = principal.navIdent,
            email = principal.email,
        )
        if (!updateResponse.isOk) {
            logger.warn("Failed to update participant identity: ${updateResponse.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        return ResponseEntity.ok(
            ProgramParticipantView(
                id = participant.id,
                email = principal.email,
                fullname = participant.fullname,
                active = participant.status == "ACTIVE",
                joinedAt = participant.createdAt,
                teams = participant.teams,
            )
        )
    }

    @GetMapping("/events")
    fun fetchEvents(): ResponseEntity<Any> {
        val events = eventRepository.getAllEvents()
        if (!events.isOk) {
            logger.warn("Failed to fetch events from database: ${events.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }

        return ResponseEntity.ok(events.queryResult)
    }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
