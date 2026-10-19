package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.SlackChannelParticipationJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.membership.SlackChannelParticipationOverview
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/slack/channel-participation")
@Tag(name = "Slack integration")
class AdminSlackChannelParticipationController(private val job: SlackChannelParticipationJob) {
    @GetMapping
    @Operation(summary = "Get Slack channel participation status")
    fun overview(): SlackChannelParticipationOverview = job.overview()

    @PostMapping("/check")
    @Operation(summary = "Check Slack channel participation")
    @ApiResponse(responseCode = "202", description = "Check accepted")
    fun check(): ResponseEntity<Void> =
        when (job.triggerManualCheck(principal().email)) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> throw ApiRequestException(
                HttpStatus.CONFLICT, "Check already running", "A Slack channel check is already running",
            )
            SyncTriggerResult.DISABLED -> throw ApiRequestException(
                HttpStatus.CONFLICT, "Check disabled", "Slack channel participation checks are disabled",
            )
            SyncTriggerResult.UNAVAILABLE -> throw ApiRequestException(
                HttpStatus.SERVICE_UNAVAILABLE, "Check unavailable", "The Slack channel check could not be started",
            )
        }

    private fun principal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
