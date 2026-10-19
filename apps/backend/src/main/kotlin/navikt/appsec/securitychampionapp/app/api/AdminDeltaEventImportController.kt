package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.DeltaEventImportJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/delta/events")
@Tag(name = "Delta integration")
class AdminDeltaEventImportController(
    private val deltaEventImportJob: DeltaEventImportJob,
) {
    @PostMapping("/sync")
    @Operation(summary = "Start an import of Delta events")
    @ApiResponse(responseCode = "202", description = "Import accepted")
    fun triggerImport(): ResponseEntity<Void> =
        when (deltaEventImportJob.triggerManualImport(currentPrincipal().email)) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Import already running",
                "A Delta event import is already running",
            )
            SyncTriggerResult.UNAVAILABLE -> throw ApiRequestException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Import unavailable",
                "The Delta event import could not be started",
            )
            SyncTriggerResult.DISABLED -> throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Import disabled",
                "The Delta event import is disabled",
            )
        }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
