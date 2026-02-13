package navikt.appsec.securitychampionstats.common.security.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


data class TokenResponse(
    val active: Boolean,
    val claims: Map<String, JsonElement>
)

@Suppress("PropertyName")
@Serializable
data class IntrospectionRequest(
    val identity_provider: String,
    val token: String
)