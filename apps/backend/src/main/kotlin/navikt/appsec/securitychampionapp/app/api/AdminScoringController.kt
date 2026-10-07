package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.AdminScoringOverview
import navikt.appsec.securitychampionapp.app.scoring.ActivityCredit
import navikt.appsec.securitychampionapp.app.scoring.InvalidScoringRequestException
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustment
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustmentRequest
import navikt.appsec.securitychampionapp.app.scoring.ResetSeasonRequest
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.UpdateSeasonResetDateRequest
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

@RestController
@RequestMapping("/api/admin/scoring")
class AdminScoringController(
    private val scoringService: ScoringService,
) {
    @GetMapping
    fun overview(): ResponseEntity<AdminScoringOverview> =
        ResponseEntity.ok(scoringService.adminOverview())

    @GetMapping("/participants/{id}/credits")
    fun participantCredits(@PathVariable id: String): ResponseEntity<List<ActivityCredit>> {
        val participantId = id.toUuid()
            ?: throw InvalidScoringRequestException("The participant ID is invalid")
        return ResponseEntity.ok(scoringService.creditsForParticipant(participantId))
    }

    @PostMapping("/participants/{id}/adjustments")
    fun addAdjustment(
        @PathVariable id: String,
        @RequestBody request: PointAdjustmentRequest,
    ): ResponseEntity<Any> {
        val participantId = id.toUuid() ?: throw InvalidScoringRequestException("The participant ID is invalid")
        val sourceCreditId = request.sourceCreditId?.toUuid()
            ?: if (request.sourceCreditId == null) null else
                throw InvalidScoringRequestException("The source credit ID is invalid")
        val result: PointAdjustment = scoringService.addAdjustment(
            participantId,
            request.pointsDelta,
            request.reason,
            currentPrincipal().email,
            sourceCreditId,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PutMapping("/season/reset-date")
    fun updateNextResetDate(
        @RequestBody request: UpdateSeasonResetDateRequest,
    ): ResponseEntity<Any> {
        val resetDate = try {
            LocalDate.parse(request.nextResetDate)
        } catch (_: DateTimeParseException) {
            throw ApiRequestException(
                HttpStatus.BAD_REQUEST,
                "Invalid reset date",
                "Use an ISO date",
            )
        }
        return ResponseEntity.ok(scoringService.updateNextResetDate(resetDate, currentPrincipal().email))
    }

    @PostMapping("/season/reset")
    fun resetSeason(@RequestBody request: ResetSeasonRequest): ResponseEntity<Any> =
        ResponseEntity.ok(
            scoringService.resetManually(
                request.confirmed,
                request.reason,
                currentPrincipal().email,
            )
        )

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal

    private fun String.toUuid(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
}
