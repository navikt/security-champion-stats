package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.scoring.InvalidScoringRequestException
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class ScoreHistoryController(
    private val scoringService: ScoringService,
    private val participantRepository: ParticipantStore,
) {
    @GetMapping("/me/score-summary")
    fun ownSummary(@RequestParam(required = false) season: String?): ResponseEntity<Any> {
        val participantId = ownParticipantId()
        return ResponseEntity.ok(scoringService.scoreSummaryForParticipant(participantId, season, admin = false))
    }

    @GetMapping("/me/score-history")
    fun ownHistory(
        @RequestParam(required = false) season: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<Any> {
        val participantId = ownParticipantId()
        return ResponseEntity.ok(
            scoringService.participantScoreHistoryPage(
                participantId = participantId,
                season = season,
                type = type,
                cursor = cursor,
                limit = limit,
                admin = false,
            ),
        )
    }

    @GetMapping("/participants/{id}/score-summary")
    fun adminSummary(
        @PathVariable id: String,
        @RequestParam(required = false) season: String?,
    ): ResponseEntity<Any> {
        val participantId = id.toUuid()
        return ResponseEntity.ok(scoringService.scoreSummaryForParticipant(participantId, season, admin = true))
    }

    @GetMapping("/participants/{id}/score-history")
    fun adminHistory(
        @PathVariable id: String,
        @RequestParam(required = false) season: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<Any> {
        val participantId = id.toUuid()
        return ResponseEntity.ok(
            scoringService.participantScoreHistoryPage(
                participantId = participantId,
                season = season,
                type = type,
                cursor = cursor,
                limit = limit,
                admin = true,
            ),
        )
    }

    private fun ownParticipantId(): UUID {
        val principal = requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
        val participant = participantRepository.findByNavNoEmail(principal.email)
            ?: throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Participant not found",
                "Program participation does not exist",
            )
        return participant.id
    }

    private fun String.toUuid(): UUID =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            throw InvalidScoringRequestException("The participant ID is invalid")
        }
}
