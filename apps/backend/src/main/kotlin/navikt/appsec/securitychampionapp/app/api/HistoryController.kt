package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.audit.ParticipantHistoryEntry
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class HistoryController(
    private val participantRepository: ProgramParticipantRepository,
    private val auditService: ProgramAuditService,
) {
    private val logger = LoggerFactory.getLogger(HistoryController::class.java)

    @GetMapping("/history")
    fun history(): ResponseEntity<List<ParticipantHistoryEntry>> {
        val principal = requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
        val participantResponse = participantRepository.findByNavNoEmail(principal.email)
        if (!participantResponse.isOk) {
            logger.error("Could not resolve authenticated participant history")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        val participant = participantResponse.queryResult.firstOrNull()
            ?: return ResponseEntity.notFound().build()
        val participantId = try {
            UUID.fromString(participant.id)
        } catch (_: IllegalArgumentException) {
            logger.error("Authenticated participant history contains an invalid participant identifier")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        return ResponseEntity.ok(auditService.participantHistory(participantId))
    }
}
