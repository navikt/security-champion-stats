package navikt.appsec.securitychampionstats.integration.zoom

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import navikt.appsec.securitychampionstats.integration.zoom.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
        return try {
            val basiAuth = Base64.getEncoder()
                .encodeToString("$clientId:$clientSecret".toByteArray())
            val response = zoomOauthWebClient.post()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/oauth/token")
                        .queryParam("grant_type", "account_credentials")
                        .build()
                }
                .header(HttpHeaders.AUTHORIZATION, "Basic $basiAuth")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(BodyInserters.fromFormData("grant_type", "account_credentials"))
                .retrieve()
                .onStatus({ status -> status.isError }) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { body ->
                        RuntimeException("Error from Zoom token endpoint: ${clientResponse.statusCode()} body=$body")
                    }
                }
                .bodyToMono<TokenResponse>()
                .block() ?: TokenResponse("", "", 0, "")
            response.accessToken
        } catch (e: Exception) {
            logger.error("Error fetching access token from Zoom: ${e.message}")
            TokenResponse("", "", 0, "").accessToken
        }
    }
}