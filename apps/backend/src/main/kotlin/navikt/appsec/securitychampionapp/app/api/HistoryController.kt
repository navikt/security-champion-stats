package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.audit.ParticipantHistoryEntry
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HistoryController(
    private val participantRepository: ParticipantStore,
    private val auditService: ProgramAuditService,
) {
    @GetMapping("/history")
    fun history(): ResponseEntity<List<ParticipantHistoryEntry>> {
        val principal = requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
        val participant = participantRepository.findByNavNoEmail(principal.email)
            ?: throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Participant not found",
                "Program participation does not exist",
            )
        return ResponseEntity.ok(auditService.participantHistory(participant.id))
    }
}
