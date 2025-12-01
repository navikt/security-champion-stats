package navikt.appsec.securitychampionstats.integration.zoom

import navikt.appsec.securitychampionstats.integration.zoom.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.Base64


@Service
class ZoomAuthService(
    private val zoomOauthWebClient: WebClient,
    @Value("\${zoom.oauth2.clientId}") private val clientId: String,
    @Value("\${zoom.oauth2.clientSecret}") private val clientSecret: String,
    @Value("\${zoom.oauth2.accountId}") private val accountId: String
) {
    private val logger = LoggerFactory.getLogger(ZoomAuthService::class.java)

    fun getAccessToken(): String {
        val basiAuth = Base64.getEncoder()
            .encodeToString("$clientId:$clientSecret".toByteArray())
        logger.info("Fetching access token from Zoom for accountId: $accountId")
        val response = zoomOauthWebClient.post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/oauth/token")
                    .queryParam("grant_type", "account_credentials")
                    .queryParam("account_id", accountId)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Basic $basiAuth")
            .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .body(BodyInserters.fromFormData("grant_type", "account_credentials"))
            .retrieve()
            .bodyToMono<TokenResponse>()
            .block() ?: throw IllegalStateException("Failed to fetch access token from Zoom")
        return response.accessToken
    }
}