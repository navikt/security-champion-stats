package navikt.appsec.securitychampionapp.integrations.teamCatalog

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import navikt.appsec.securitychampionapp.app.membership.ChampionRole
import navikt.appsec.securitychampionapp.app.membership.ChampionRoleSource
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogTeam
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChampionRoleTeams(
    val content: List<TeamCatalogTeam>,
    val pages: Int,
    val totalElements: Int,
)

@Service
class TeamCatalogChampionRoles(
    private val externalServiceWebClient: WebClient,
    private val teamCatalogMock: TeamCatalogMock,
    private val environment: Environment,
) : ChampionRoleSource {
    override fun fetchRoles(): Map<String, ChampionRole> {
        val teams = if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            teamCatalogMock.loadMockTeamMembers(teamCatalogMock.loadMockProductAreas()).flatMap { it.content }
        } else {
            val response = requireNotNull(
                externalServiceWebClient.get().uri("/team?status=ACTIVE").retrieve()
                    .bodyToMono<ChampionRoleTeams>().block(Duration.ofSeconds(30)),
            ) { "Teamkatalogen role response was empty" }
            check(response.pages == 1 && response.totalElements == response.content.size) {
                "Teamkatalogen role response was incomplete"
            }
            response.content
        }
        check(teams.isNotEmpty()) { "Teamkatalogen returned no active teams for role verification" }
        return teams.flatMap { it.members }.filter { !it.resource.email.isNullOrBlank() }
            .groupBy { requireNotNull(it.resource.email).lowercase() }
            .mapValues { (_, memberships) ->
                if (memberships.any { "SECURITY_CHAMPION" in it.roles }) ChampionRole.PRESENT else ChampionRole.ABSENT
            }
    }
}
