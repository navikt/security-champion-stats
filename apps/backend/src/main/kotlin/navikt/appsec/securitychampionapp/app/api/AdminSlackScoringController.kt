package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.AddSlackAccountMappingRequest
import navikt.appsec.securitychampionapp.app.scoring.InvalidScoringRequestException
import navikt.appsec.securitychampionapp.app.scoring.SlackMappingOverview
import navikt.appsec.securitychampionapp.app.scoring.SlackScoringService
import navikt.appsec.securitychampionapp.app.jobs.SlackScoringSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.dao.DuplicateKeyException
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
        when (slackScoringSyncJob.triggerManualSync()) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).build()
            SyncTriggerResult.UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
            SyncTriggerResult.DISABLED -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping("/mappings")
    fun addMapping(@RequestBody request: AddSlackAccountMappingRequest): ResponseEntity<Any> {
        val participantId = request.participantId.toUuid() ?: return ResponseEntity.badRequest().build()
        return try {
            if (!slackScoringService.addMapping(request.slackUserId, participantId, currentPrincipal().email)) {
                return ResponseEntity.notFound().build()
            }
            ResponseEntity.status(HttpStatus.CREATED).build()
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (_: DuplicateKeyException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "The Slack account is already mapped"))
        }
    }

    @DeleteMapping("/mappings/{slackUserId}")
    fun removeMapping(@PathVariable slackUserId: String): ResponseEntity<Any> =
        try {
            if (!slackScoringService.removeMapping(slackUserId, currentPrincipal().email)) {
                ResponseEntity.notFound().build()
            } else {
                ResponseEntity.noContent().build()
            }
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
