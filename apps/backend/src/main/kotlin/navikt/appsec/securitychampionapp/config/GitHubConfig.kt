package navikt.appsec.securitychampionapp.config

import navikt.appsec.securitychampionapp.integrations.github.GitHubApiClient
import navikt.appsec.securitychampionapp.integrations.github.GitHubAppTokenProvider
import navikt.appsec.securitychampionapp.integrations.github.GitHubContributionSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import java.time.Clock
import java.time.Duration

@Configuration
class GitHubConfig {
    @Bean
    fun gitHubContributionSource(
        @Value($$"${github.app-id:}") appId: String,
        @Value($$"${github.installation-id:}") installationId: String,
        @Value($$"${github.private-key:}") privateKey: String,
        clock: Clock,
    ): GitHubContributionSource {
        val client = WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader(HttpHeaders.USER_AGENT, "navikt-security-champion-stats")
            .codecs { it.defaultCodecs().maxInMemorySize(8 * 1024 * 1024) }
            .clientConnector(
                ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(30))),
            )
            .build()
        return GitHubApiClient(client, GitHubAppTokenProvider(appId, installationId, privateKey, client, clock))
    }
}
