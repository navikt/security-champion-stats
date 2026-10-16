package navikt.appsec.securitychampionapp.integrations.teamCatalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class TeamCatalogTest {
    private val teamCatalog = TeamCatalog(
        externalServiceWebClient = WebClient.builder().exchangeFunction { request ->
            val file = when (request.url().path) {
                "/productarea" -> "team_catalog_fetch_productArea.json"
                "/team" -> "team_catalog_fetch_team_" +
                    request.url().query.substringAfter("productAreaId=").substringBefore("&") + ".json"
                else -> error("Unexpected Teamkatalogen request: ${request.url()}")
            }
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(ClassPathResource("team-catalog-stub/__files/$file").inputStream.readAllBytes().decodeToString())
                    .build(),
            )
        }.build(),
    )

    @Test
    fun `should return employee profiles regardless of Teamkatalogen role`() {
        val members = teamCatalog.fetchAllMembersWithTeamData()

        assertThat(members.map { it.email }).contains(
            "local.user@nav.no",
            "test.team.lead@example.com",
        )
    }
}
