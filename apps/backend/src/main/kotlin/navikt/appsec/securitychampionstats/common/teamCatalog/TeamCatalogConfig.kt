package navikt.appsec.securitychampionstats.common.teamCatalog

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class TeamCatalogConfig(
    @Value($$"${teamCatalog.url}") private val url: String,
) {
    @Bean
    fun externalServiceWebClient(builder: WebClient.Builder): WebClient =
        builder
            .baseUrl(url)
            .defaultHeader("accept", "application/json")
            .defaultHeader("Nav-Consumer-Id", "security-champion-slackbot")
            .build()
}