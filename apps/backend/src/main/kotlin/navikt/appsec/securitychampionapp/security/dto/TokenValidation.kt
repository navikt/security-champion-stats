package navikt.appsec.securitychampionapp.security.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenResponse(
    val active: Boolean,
    val claims: Claims?,
    val error: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Claims(
    @JsonProperty("preferred_username")
    val preferredUsername: String?,
    @JsonProperty("NAVident")
    val ident: String?,
    val groups: List<String> = emptyList()
)


@Suppress("PropertyName")
data class IntrospectionRequest(
    val identity_provider: String,
    val token: String
)