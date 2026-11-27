package navikt.appsec.securitychampionapp.config

import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalogOboTokenProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class TeamCatalogConfig {
    private val size = DataSize.ofMegabytes(16).toBytes().toInt()

    @Bean
    fun externalServiceWebClient(
        @Value("\${team-catalog.url}") url: String,
    ): WebClient =
        WebClient
            .builder()
            .baseUrl(url)
            .codecs { codecs ->
                codecs.defaultCodecs().maxInMemorySize(size)
            }
            .defaultHeader("accept", "*/*")
            .build()

    @Bean
    fun teamCatalogOboTokenProvider(
        @Value("\${team-catalog.roles.token-exchange-endpoint}") tokenExchangeEndpoint: String,
        @Value("\${team-catalog.roles.target}") target: String,
    ): TeamCatalogOboTokenProvider =
        TeamCatalogOboTokenProvider(
            tokenClient = WebClient.builder().build(),
            tokenExchangeEndpoint = tokenExchangeEndpoint,
            target = target,
        )
}