package navikt.appsec.securitychampionapp.config

import navikt.appsec.securitychampionapp.integrations.delta.DeltaApiClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import java.time.Clock
import java.time.Duration
import reactor.netty.http.client.HttpClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector

@Configuration
class DeltaConfig(
    @Value($$"${delta.api.base-url}") private val apiBaseUrl: String,
    @Value($$"${NAIS_TOKEN_ENDPOINT:}") private val tokenEndpoint: String,
    @Value($$"${delta.api.target}") private val target: String,
) {
    @Bean
    fun deltaApiClient(): DeltaApiClient =
        DeltaApiClient(
            apiClient = WebClient.builder().baseUrl(apiBaseUrl)
                .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(10))))
                .build(),
            tokenClient = WebClient.builder()
                .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(10))))
                .build(),
            tokenEndpoint = tokenEndpoint,
            target = target,
        )

    @Bean
    fun deltaScoringClock(): Clock = Clock.systemUTC()
}
