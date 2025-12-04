package navikt.appsec.securitychampionstats.common.teamCatalog

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class TeamCatalogConfig() {
    //private val size = DataSize.ofMegabytes(16).toBytes().toInt()
    private val url = "http://team-catalog-backend.org.svc.cluster.local"
    @Bean
    fun externalServiceWebClient(builder: WebClient.Builder): WebClient =
        builder
            .baseUrl(url)
            .defaultHeaders { headers ->
                headers.add("accept", "json")
                headers.add(HttpHeaders.CONTENT_TYPE, "json")
                headers.add(HttpHeaders.USER_AGENT, "NAV IT McBotFace")
            }

            .build()
}