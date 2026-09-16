package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.api.dto.AppSecDashboard
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/appsec"])
class AppSecController(
    private val repo: PostgresRepository,
) {

    @GetMapping("/dashboard")
    fun getDashboard(): ResponseEntity<AppSecDashboard> {
        val data = repo.getSCAmountOverTime()
        val response = AppSecDashboard(
            data = data,
            notice = "FLAG{security_through_obscurity_is_not_access_control}"
        )
        return ResponseEntity.ok(response)
    }
}
