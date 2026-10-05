package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.AdminScoringOverview
import navikt.appsec.securitychampionapp.app.scoring.ActivityCredit
import navikt.appsec.securitychampionapp.app.scoring.InvalidScoringRequestException
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustment
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustmentRequest
import navikt.appsec.securitychampionapp.app.scoring.ResetSeasonRequest
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.app.scoring.ScoringTargetNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.SourceCreditNotFoundException
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
        val participantId = id.toUuid() ?: return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(scoringService.creditsForParticipant(participantId))
        } catch (_: ScoringTargetNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/participants/{id}/adjustments")
    fun addAdjustment(
        @PathVariable id: String,
        @RequestBody request: PointAdjustmentRequest,
    ): ResponseEntity<Any> {
        val participantId = id.toUuid() ?: return ResponseEntity.badRequest().build()
        val sourceCreditId = request.sourceCreditId?.toUuid()
            ?: if (request.sourceCreditId == null) null else return ResponseEntity.badRequest().build()
        return try {
            val result: PointAdjustment = scoringService.addAdjustment(
                participantId,
                request.pointsDelta,
                request.reason,
                currentPrincipal().email,
                sourceCreditId,
            )
            ResponseEntity.status(HttpStatus.CREATED).body(result)
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (_: ScoringTargetNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (_: SourceCreditNotFoundException) {
            ResponseEntity.badRequest().body(mapOf("error" to "The source credit does not belong to the participant"))
        }
    }

    @PutMapping("/season/reset-date")
    fun updateNextResetDate(
        @RequestBody request: UpdateSeasonResetDateRequest,
    ): ResponseEntity<Any> {
        val resetDate = try {
            LocalDate.parse(request.nextResetDate)
        } catch (_: DateTimeParseException) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Use an ISO date"))
        }
        return try {
            ResponseEntity.ok(scoringService.updateNextResetDate(resetDate, currentPrincipal().email))
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/season/reset")
    fun resetSeason(@RequestBody request: ResetSeasonRequest): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(
                scoringService.resetManually(
                    request.confirmed,
                    request.reason,
                    currentPrincipal().email,
                )
            )
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal

    private fun String.toUuid(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
}
