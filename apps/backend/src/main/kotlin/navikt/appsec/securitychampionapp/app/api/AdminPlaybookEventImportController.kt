package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.PlaybookEventImportJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/playbook/events")
class AdminPlaybookEventImportController(private val importJob: PlaybookEventImportJob) {
    @PostMapping("/sync")
    fun triggerImport(): ResponseEntity<Void> =
        when (importJob.triggerManualImport()) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).build()
            SyncTriggerResult.UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
            SyncTriggerResult.DISABLED -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
}
