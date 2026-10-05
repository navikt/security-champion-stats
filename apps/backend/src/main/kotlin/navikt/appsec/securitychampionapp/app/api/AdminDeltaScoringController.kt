package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.DeltaScoringSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncStatusView
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/delta")
class AdminDeltaScoringController(
    private val statusService: DeltaScoringStatusService,
    private val deltaScoringSyncJob: DeltaScoringSyncJob,
) {
    @GetMapping("/sync-status")
    fun syncStatus(): ResponseEntity<DeltaSyncStatusView> =
        ResponseEntity.ok(statusService.status())

    @PostMapping("/sync")
    fun triggerSync(): ResponseEntity<Void> =
        when (deltaScoringSyncJob.triggerManualSync()) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).build()
            SyncTriggerResult.UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
            SyncTriggerResult.DISABLED -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
}
