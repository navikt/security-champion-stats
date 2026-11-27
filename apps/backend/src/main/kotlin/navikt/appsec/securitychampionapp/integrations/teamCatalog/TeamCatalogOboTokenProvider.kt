package navikt.appsec.securitychampionapp.integrations.teamCatalog

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.core.codec.DecodingException
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.util.concurrent.TimeoutException

class TeamCatalogAuthenticationException : RuntimeException("Teamkatalogen authentication failed")

class TeamCatalogOboTokenProvider(
    private val tokenClient: WebClient,
    private val tokenExchangeEndpoint: String,
    private val target: String,
) {
    fun token(): String {
        if (tokenExchangeEndpoint.isBlank() || target.isBlank()) throw TeamCatalogAuthenticationException()
        val userToken = (SecurityContextHolder.getContext().authentication?.credentials as? String)
            ?.takeIf(String::isNotBlank)
            ?: throw TeamCatalogAuthenticationException()

        return try {
            tokenClient.post()
                .uri(tokenExchangeEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    BodyInserters.fromFormData("identity_provider", "entra_id")
                        .with("target", target)
                        .with("user_token", userToken),
                )
                .retrieve()
                .bodyToMono<ExchangedToken>()
                .block(Duration.ofSeconds(10))
                ?.accessToken
                ?.takeIf(String::isNotBlank)
                ?: throw TeamCatalogAuthenticationException()
        } catch (_: WebClientException) {
            throw TeamCatalogAuthenticationException()
        } catch (_: DecodingException) {
            throw TeamCatalogAuthenticationException()
        } catch (e: IllegalStateException) {
            if (e.cause !is TimeoutException) throw e
            throw TeamCatalogAuthenticationException()
        }
    }

    private class ExchangedToken(
        @param:JsonProperty("access_token")
        val accessToken: String,
    )
}
