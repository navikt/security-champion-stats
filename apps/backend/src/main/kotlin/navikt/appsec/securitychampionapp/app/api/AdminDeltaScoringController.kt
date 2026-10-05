package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncStatusView
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/delta")
class AdminDeltaScoringController(
    private val statusService: DeltaScoringStatusService,
) {
    @GetMapping("/sync-status")
    fun syncStatus(): ResponseEntity<DeltaSyncStatusView> =
        ResponseEntity.ok(statusService.status())
}
