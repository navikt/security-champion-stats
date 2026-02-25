package navikt.appsec.securitychampionstats.common.security.local

import navikt.appsec.securitychampionstats.common.security.dto.IntrospectionRequest
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
@RequestMapping("/internal/local-auth")
class LocalTokenIntrospectionController(
    private val localTokenService: LocalTokenService
) {
    @PostMapping("/introspect")
    fun introspect(@RequestBody request: IntrospectionRequest): Map<String, Any> =
        localTokenService.introspect(request)
}

