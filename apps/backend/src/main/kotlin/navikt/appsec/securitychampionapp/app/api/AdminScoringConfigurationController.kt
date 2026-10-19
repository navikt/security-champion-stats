package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.ScoringConfigurationRequest
import navikt.appsec.securitychampionapp.app.scoring.ScoringConfigurationService
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/scoring/configuration")
@Tag(name = "Scoring")
class AdminScoringConfigurationController(private val service: ScoringConfigurationService) {
    @GetMapping
    @Operation(summary = "Get scoring configuration")
    fun configuration() = service.configuration()

    @PostMapping("/preview")
    @Operation(summary = "Preview a scoring configuration")
    fun preview(@RequestBody request: ScoringConfigurationRequest) = service.preview(request)

    @PutMapping
    @Operation(summary = "Save scoring configuration")
    fun save(@RequestBody request: ScoringConfigurationRequest) = service.save(
        request,
        (requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal).email,
    )
}
