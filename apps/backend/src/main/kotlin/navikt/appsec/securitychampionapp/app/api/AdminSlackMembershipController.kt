package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.SlackMembershipSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SlackMembershipConfiguration
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.membership.MembershipAnnouncement
import navikt.appsec.securitychampionapp.app.membership.MembershipSyncBusyException
import navikt.appsec.securitychampionapp.app.membership.MembershipPreviewChangedException
import navikt.appsec.securitychampionapp.app.membership.SlackMembershipPreview
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ResolveMembershipDeliveryRequest(val retry: Boolean)
data class SyncSlackMembershipRequest(val expectedVersion: String? = null)

@RestController
@RequestMapping("/api/admin/slack/membership")
@Tag(name = "Slack integration")
class AdminSlackMembershipController(private val job: SlackMembershipSyncJob) {
    @GetMapping
    @Operation(summary = "Get Slack membership sync configuration")
    fun configuration(): SlackMembershipConfiguration = job.configuration()

    @GetMapping("/preview")
    @Operation(summary = "Preview Slack membership synchronization")
    fun preview(): SlackMembershipPreview = job.preview()

    @GetMapping("/announcements")
    @Operation(summary = "List Slack membership announcements")
    fun announcements(): List<MembershipAnnouncement> = job.announcements()

    @PostMapping("/sync")
    @Operation(summary = "Start a Slack membership sync")
    @ApiResponse(responseCode = "202", description = "Sync accepted")
    fun sync(@RequestBody(required = false) request: SyncSlackMembershipRequest?): ResponseEntity<Void> {
        val result = try {
            job.triggerManualSync(principal().email, request?.expectedVersion)
        } catch (_: MembershipPreviewChangedException) {
            throw ApiRequestException(
                HttpStatus.CONFLICT, "Preview changed", "Preview membership changes again before starting a write-enabled sync",
            )
        }
        return when (result) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> throw ApiRequestException(
                HttpStatus.CONFLICT, "Sync already running", "A Slack membership sync is already running",
            )
            SyncTriggerResult.DISABLED -> throw ApiRequestException(
                HttpStatus.CONFLICT, "Sync disabled", "The Slack membership sync is disabled",
            )
            SyncTriggerResult.UNAVAILABLE -> throw ApiRequestException(
                HttpStatus.SERVICE_UNAVAILABLE, "Sync unavailable", "The Slack membership sync could not be started",
            )
        }
    }

    @PostMapping("/announcements/{id}/resolve")
    @Operation(summary = "Resolve an uncertain Slack announcement delivery")
    @ApiResponse(responseCode = "204", description = "Delivery resolved")
    fun resolve(@PathVariable id: UUID, @RequestBody request: ResolveMembershipDeliveryRequest): ResponseEntity<Void> {
        val resolved = try {
            job.resolveUncertain(id, request.retry, principal().email)
        } catch (_: MembershipSyncBusyException) {
            throw ApiRequestException(
                HttpStatus.CONFLICT, "Sync already running", "Wait for the Slack membership sync to finish before resolving delivery",
            )
        }
        if (!resolved) {
            throw ApiRequestException(
                HttpStatus.CONFLICT, "Delivery cannot be resolved", "The announcement is not awaiting delivery confirmation",
            )
        }
        return ResponseEntity.noContent().build()
    }

    private fun principal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
