package navikt.appsec.securitychampionstats.integration.zoom.dto


data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val scope: String,
)
