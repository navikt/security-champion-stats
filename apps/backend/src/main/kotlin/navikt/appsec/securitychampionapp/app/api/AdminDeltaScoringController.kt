package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.DeltaScoringSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncStatusView
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
@RequestMapping("/api/admin/delta")
@Tag(name = "Delta integration")
class AdminDeltaScoringController(
    private val statusService: DeltaScoringStatusService,
    private val deltaScoringSyncJob: DeltaScoringSyncJob,
) {
    @GetMapping("/sync-status")
    @Operation(summary = "Get Delta registration sync status")
    fun syncStatus(): ResponseEntity<DeltaSyncStatusView> =
        ResponseEntity.ok(statusService.status())

    @PostMapping("/sync")
    @Operation(summary = "Start a Delta registration sync")
    @ApiResponse(responseCode = "202", description = "Sync accepted")
    fun triggerSync(): ResponseEntity<Void> =
        when (deltaScoringSyncJob.triggerManualSync(currentPrincipal().email)) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Sync already running",
                "A Delta registration sync is already running",
            )
            SyncTriggerResult.UNAVAILABLE -> throw ApiRequestException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Sync unavailable",
                "The Delta registration sync could not be started",
            )
            SyncTriggerResult.DISABLED -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Sync disabled",
                "The Delta registration sync is disabled",
            )
        }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
