package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.audit.ParticipantHistoryEntry
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HistoryController(
    private val participantRepository: ParticipantStore,
    private val scoringService: ScoringService,
) {
    @GetMapping("/history")
    fun history(
        @RequestParam(required = false) season: String? = null,
        @RequestParam(required = false) type: String? = null,
        @RequestParam(required = false) cursor: String? = null,
        @RequestParam(required = false) limit: Int? = null,
    ): ResponseEntity<Any> {
        val principal = requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
        val participant = participantRepository.findByNavNoEmail(principal.email)
            ?: throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Participant not found",
                "Program participation does not exist",
            )
        return ResponseEntity.ok(
            scoringService.participantScoreHistoryPage(
                participantId = participant.id,
                season = season,
                type = type,
                cursor = cursor,
                limit = limit,
                admin = false,
            ),
        )
    }
}
