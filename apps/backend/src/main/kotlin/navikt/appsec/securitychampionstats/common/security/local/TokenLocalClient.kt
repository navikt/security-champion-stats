package navikt.appsec.securitychampionstats.common.security.local

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import navikt.appsec.securitychampionstats.common.security.dto.TokenResponse

class TokenLocalClient {
    fun validate(token: String): TokenResponse {
        if (token.contains("invalid", ignoreCase = true)) {
            return TokenResponse(
                active = false,
                claims = emptyMap()
            )
        }
        return TokenResponse(
            active = true,
            claims = mapOf(
                "preferred_username" to JsonPrimitive("lokal.utvikler@nav.no"),
                "NAVident" to JsonPrimitive("D112345"),
                "groups" to JsonArray(listOf(
                    JsonPrimitive("1234567890"),
                    JsonPrimitive("test-id-4b9d-984e-85499f126e18")
                ))
            )
        )
    }
}