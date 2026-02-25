package navikt.appsec.securitychampionstats.common.security.local

import navikt.appsec.securitychampionstats.common.security.local.dto.LocalTokenRequest
import navikt.appsec.securitychampionstats.common.security.local.dto.LocalTokenResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
@RequestMapping("/auth/local")
class LocalAuthController(
    private val localTokenService: LocalTokenService
) {
    @PostMapping("/token")
    fun issueToken(@RequestBody request: LocalTokenRequest): LocalTokenResponse =
        localTokenService.issueToken(request)
}

