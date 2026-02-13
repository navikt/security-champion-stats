package navikt.appsec.securitychampionstats.common.security.client

import navikt.appsec.securitychampionstats.common.security.dto.IntrospectionRequest
import navikt.appsec.securitychampionstats.common.security.dto.TokenResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class TokenValidationClient(
    builder: RestClient.Builder
) {
    private val client = builder.build()

    fun validate(url: String, token: String, identityProvider: String): TokenResponse {
        return client.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(IntrospectionRequest(
                identityProvider,
                token
            ))
            .retrieve()
            .body<TokenResponse>()
            ?: TokenResponse(active = false, claims = emptyMap())
    }
}