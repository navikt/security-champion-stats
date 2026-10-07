package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.jobs.GitHubScoringSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/github")
class AdminGitHubScoringController(private val job: GitHubScoringSyncJob) {
    @PostMapping("/sync")
    fun sync(): ResponseEntity<Void> {
        val principal = requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
        return when (job.triggerManualSync(principal.email)) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING, SyncTriggerResult.DISABLED ->
                ResponseEntity.status(HttpStatus.CONFLICT).build()
            SyncTriggerResult.UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
    }
}
