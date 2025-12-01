package navikt.appsec.securitychampionstats.common.zoom

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class ZoomClientConfig(
    @Value("\${zoom.oauth2.issuer}") val issuer: String,
    @Value("\${zoom.oauth2.apiUrl}") val apiUrl: String,
) {

    @Bean
    fun zoomOauthWebClient(builder: WebClient.Builder): WebClient =
        builder
            .baseUrl(issuer)
            .build()

    @Bean
    fun zoomApiWebClient(builder: WebClient.Builder): WebClient =
        builder
            .baseUrl(apiUrl)
            .build()
}