package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.LeaderboardEntry
import navikt.appsec.securitychampionapp.app.scoring.OwnSeasonScore
import navikt.appsec.securitychampionapp.app.scoring.RecognitionEntry
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.slf4j.LoggerFactory
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
    private val participantRepository: ProgramParticipantRepository,
) {
    private val logger = LoggerFactory.getLogger(ScoringController::class.java)

    @GetMapping("/recognition")
    fun recognition(): ResponseEntity<List<RecognitionEntry>> =
        ResponseEntity.ok(scoringService.recognition())

    @GetMapping("/leaderboard")
    fun leaderboard(): ResponseEntity<List<LeaderboardEntry>> {
        val authentication = requireNotNull(SecurityContextHolder.getContext().authentication)
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_$ADMIN_ROLE" }
        if (!isAdmin) {
            val principal = authentication.principal as AppPrincipal
            val participant = participantRepository.findByNavNoEmail(principal.navNoEmail)
            if (!participant.isOk) {
                logger.error("Failed to validate leaderboard access: ${participant.error}")
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }
            if (participant.queryResult.none { it.status == "ACTIVE" }) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
        }
        return ResponseEntity.ok(scoringService.leaderboard())
    }

    @GetMapping("/scoring/me")
    fun ownScore(): ResponseEntity<OwnSeasonScore> {
        val principal = currentPrincipal()
        val participant = participantRepository.findByNavNoEmail(principal.navNoEmail)
        if (!participant.isOk) {
            logger.error("Failed to find participant score: ${participant.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        val activeParticipant = participant.queryResult.firstOrNull { it.status == "ACTIVE" }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(scoringService.ownScore(java.util.UUID.fromString(activeParticipant.id)))
    }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
