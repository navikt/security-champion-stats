package navikt.appsec.securitychampionapp.integrations.teamCatalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper

class TeamCatalogTest {
    private val environment = StandardEnvironment().apply { setActiveProfiles("test") }
    private val teamCatalog = TeamCatalog(
        externalServiceWebClient = WebClient.builder().build(),
        teamCatalogMock = TeamCatalogMock(ObjectMapper(), DefaultResourceLoader()),
        environment = environment,
    )

    @Test
    fun `should return employee profiles regardless of Teamkatalogen role`() {
        val members = teamCatalog.fetchAllMembersWithTeamData()

        assertThat(members.map { it.email }).contains(
            "ada.lovelace@nav.no",
            "mina.haugen@nav.no",
        )
    }
}
