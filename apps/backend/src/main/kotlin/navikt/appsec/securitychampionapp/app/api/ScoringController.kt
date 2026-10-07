package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.LeaderboardEntry
import navikt.appsec.securitychampionapp.app.scoring.OwnSeasonScore
import navikt.appsec.securitychampionapp.app.scoring.RecognitionEntry
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ScoringController(
    private val scoringService: ScoringService,
    private val participantRepository: ParticipantStore,
) {
    @GetMapping("/recognition")
    fun recognition(): ResponseEntity<List<RecognitionEntry>> =
        ResponseEntity.ok(scoringService.recognition())

    @GetMapping("/leaderboard")
    fun leaderboard(): ResponseEntity<List<LeaderboardEntry>> {
        val authentication = requireNotNull(SecurityContextHolder.getContext().authentication)
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_$ADMIN_ROLE" }
        val principal = authentication.principal as AppPrincipal
        val participant = participantRepository.findByNavNoEmail(principal.email)
        if (!isAdmin && participant?.status != ParticipationStatus.ACTIVE) {
            throw ApiRequestException(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "An active program membership is required to view the leaderboard",
            )
        }
        val currentParticipantId = participant
            ?.takeIf { it.status == ParticipationStatus.ACTIVE }
            ?.id
        return ResponseEntity.ok(scoringService.leaderboard(currentParticipantId))
    }

    @GetMapping("/scoring/me")
    fun ownScore(): ResponseEntity<OwnSeasonScore> {
        val principal = currentPrincipal()
        val participant = participantRepository.findByNavNoEmail(principal.email)
        if (participant?.status != ParticipationStatus.ACTIVE) {
            throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Score not found",
                "An active program membership is required to view a personal score",
            )
        }
        return ResponseEntity.ok(scoringService.ownScore(participant.id))
    }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
