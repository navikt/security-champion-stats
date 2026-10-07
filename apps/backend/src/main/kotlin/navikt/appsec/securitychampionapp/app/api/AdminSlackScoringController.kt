package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.AddSlackAccountMappingRequest
import navikt.appsec.securitychampionapp.app.scoring.SlackMappingOverview
import navikt.appsec.securitychampionapp.app.scoring.SlackScoringService
import navikt.appsec.securitychampionapp.app.jobs.SlackScoringSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/slack")
class AdminSlackScoringController(
    private val slackScoringService: SlackScoringService,
    private val slackScoringSyncJob: SlackScoringSyncJob,
) {
    @GetMapping
    fun overview(): ResponseEntity<SlackMappingOverview> =
        ResponseEntity.ok(slackScoringService.mappingOverview())

    @PostMapping("/sync")
    fun triggerSync(): ResponseEntity<Void> =
        when (slackScoringSyncJob.triggerManualSync(currentPrincipal().email)) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Sync already running",
                "A Slack sync is already running",
            )
            SyncTriggerResult.UNAVAILABLE -> throw ApiRequestException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Sync unavailable",
                "The Slack sync could not be started",
            )
            SyncTriggerResult.DISABLED -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Sync disabled",
                "The Slack sync is disabled",
            )
        }

    @PostMapping("/mappings")
    fun addMapping(@RequestBody request: AddSlackAccountMappingRequest): ResponseEntity<Any> {
        val participantId = request.participantId.toUuid() ?: throw ApiRequestException(
            HttpStatus.BAD_REQUEST,
            "Invalid participant ID",
            "The participant ID is invalid",
        )
        if (!slackScoringService.addMapping(request.slackUserId, participantId, currentPrincipal().email)) {
            throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Participant not found",
                "The participant does not exist",
            )
        }
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/mappings/{slackUserId}")
    fun removeMapping(@PathVariable slackUserId: String): ResponseEntity<Any> =
        if (!slackScoringService.removeMapping(slackUserId, currentPrincipal().email)) {
            throw ApiRequestException(
                HttpStatus.NOT_FOUND,
                "Slack mapping not found",
                "The Slack account mapping does not exist",
            )
        } else {
            ResponseEntity.noContent().build()
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
