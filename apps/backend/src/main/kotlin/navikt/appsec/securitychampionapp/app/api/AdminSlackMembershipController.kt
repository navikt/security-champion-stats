package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.SlackMembershipSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SlackMembershipConfiguration
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.membership.MembershipAnnouncement
import navikt.appsec.securitychampionapp.app.membership.MembershipSyncBusyException
import navikt.appsec.securitychampionapp.app.membership.SlackMembershipPreview
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ResolveMembershipDeliveryRequest(val retry: Boolean)

@RestController
@RequestMapping("/api/admin/slack/membership")
class AdminSlackMembershipController(private val job: SlackMembershipSyncJob) {
    @GetMapping
    fun configuration(): SlackMembershipConfiguration = job.configuration()

    @GetMapping("/preview")
    fun preview(): SlackMembershipPreview = job.preview()

    @GetMapping("/announcements")
    fun announcements(): List<MembershipAnnouncement> = job.announcements()

    @PostMapping("/sync")
    fun sync(): ResponseEntity<Void> =
        when (job.triggerManualSync(principal().email)) {
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

    @PostMapping("/announcements/{id}/resolve")
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
