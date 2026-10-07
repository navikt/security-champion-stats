package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.ScoringConfigurationRequest
import navikt.appsec.securitychampionapp.app.scoring.ScoringConfigurationService
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/scoring/configuration")
class AdminScoringConfigurationController(private val service: ScoringConfigurationService) {
    @GetMapping
    fun configuration() = service.configuration()

    @PostMapping("/preview")
    fun preview(@RequestBody request: ScoringConfigurationRequest) = service.preview(request)

    @PutMapping
    fun save(@RequestBody request: ScoringConfigurationRequest) = service.save(
        request,
        (requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal).email,
    )
}
