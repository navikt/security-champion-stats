package navikt.appsec.securitychampionstats.common.security.client

import kotlinx.serialization.json.JsonObject
import navikt.appsec.securitychampionstats.common.security.dto.IntrospectionRequest
import navikt.appsec.securitychampionstats.common.security.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class TokenValidationClient(
    builder: RestClient.Builder
) {
    private val client = builder.build()
    private val log = LoggerFactory.getLogger("TokenValidationClient")
    fun validate(url: String, token: String, identityProvider: String): TokenResponse {

        log.info("Validating token with identity provider: $identityProvider at URL: $url")
        val jsonObject =  client.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(IntrospectionRequest(
                identityProvider,
                token
            ))
            .retrieve()
            .body<JsonObject>()
            ?: throw RestClientException("Failed to retrieve token introspection response from $url")
        return TokenResponse.fromJsonObject(jsonObject)
    }
}