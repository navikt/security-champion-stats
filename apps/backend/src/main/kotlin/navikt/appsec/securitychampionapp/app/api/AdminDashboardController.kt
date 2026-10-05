package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.dashboard.AdminDashboardOverview
import navikt.appsec.securitychampionapp.app.dashboard.AdminDashboardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/dashboard")
class AdminDashboardController(
    private val dashboardService: AdminDashboardService,
) {
    @GetMapping("/overview")
    fun overview(): ResponseEntity<AdminDashboardOverview> =
        ResponseEntity.ok(dashboardService.overview())
}
