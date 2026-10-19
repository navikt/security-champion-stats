package navikt.appsec.securitychampionapp.config

import io.netty.channel.ChannelOption
import navikt.appsec.securitychampionapp.app.scoring.AdminGroupMembers
import navikt.appsec.securitychampionapp.integrations.graph.GraphAdminGroupMembers
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Clock
import java.time.Duration

@Configuration
class GraphConfig(
    @Value($$"${graph.apiUrl}") private val baseUrl: String,
    @Value($$"${graph.token-endpoint:${NAIS_TOKEN_ENDPOINT:}}") private val tokenEndpoint: String,
    @Value($$"${spring.security.token-validation.groups}") private val groupId: String,
) {
    @Bean
    fun adminGroupMembers(clock: Clock): AdminGroupMembers {
        val connector = ReactorClientHttpConnector(
            HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10)),
        )
        return GraphAdminGroupMembers(
            WebClient.builder().clientConnector(connector).build(),
            WebClient.builder().clientConnector(connector).build(),
            baseUrl, tokenEndpoint, groupId, clock,
        )
    }
}
