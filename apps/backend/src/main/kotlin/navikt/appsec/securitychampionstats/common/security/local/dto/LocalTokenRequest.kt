package navikt.appsec.securitychampionstats.common.security.local.dto

data class LocalTokenRequest(
    val navIdent: String,
    val preferredUsername: String,
    val groups: List<String>
)