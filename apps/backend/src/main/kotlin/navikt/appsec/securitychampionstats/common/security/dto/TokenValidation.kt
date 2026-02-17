package navikt.appsec.securitychampionstats.common.security.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject


data class TokenResponse(
    val active: Boolean,
    val claims: Map<String, JsonElement>
) {
    companion object {
        fun fromJsonObject(jsonObject: JsonObject): TokenResponse {
            val active = jsonObject["active"]?.toString()?.toBoolean() ?: false
            return TokenResponse(active, jsonObject.toMap())
        }
    }
}


@Suppress("PropertyName")
@Serializable
data class IntrospectionRequest(
    val identity_provider: String,
    val token: String
)