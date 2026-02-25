package navikt.appsec.securitychampionstats.common.security.local.dto

data class LocalTokenClaims(
    val navIdent: String,
    val preferredUsername: String,
    val groups: List<String>
)