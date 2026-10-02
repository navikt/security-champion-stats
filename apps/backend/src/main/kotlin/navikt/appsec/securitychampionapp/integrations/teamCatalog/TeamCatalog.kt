package navikt.appsec.securitychampionapp.integrations.teamCatalog

import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.MemberWithTeamData
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.ProductAreaResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamResponse
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class TeamCatalog(
    private val externalServiceWebClient: WebClient,
    private val teamCatalogMock: TeamCatalogMock,
    private val environment: Environment,
) {
    private val logger = LoggerFactory.getLogger(TeamCatalog::class.java)

    private fun useMockResponses(): Boolean = environment.acceptsProfiles(Profiles.of("local", "test"))

    private fun fetchAllProductAreas(): ProductAreaResponse {
        return try {
            externalServiceWebClient
                .get()
                .uri("/productarea?status=ACTIVE")
                .retrieve()
                .onStatus({ status -> status.isError}) { clientResponse ->
                    clientResponse.bodyToMono<String>().map {
                        RuntimeException("Error from Team catalog fetch product area: ${clientResponse.statusCode()}, body: $it")
                    }
                }
                .bodyToMono<ProductAreaResponse>()
                .block()
                ?: ProductAreaResponse(emptyList())
        } catch (e: Exception) {
            logger.error(e.message)
            ProductAreaResponse(
                emptyList()
            )
        }

    }

    private fun fetchAllTeams(productArea: ProductAreaResponse): List<TeamResponse> {
        if (productArea.content.isEmpty()) {
            return emptyList()
        }

        return try {
            productArea.content.map {
                externalServiceWebClient
                    .get()
                    .uri("/team?productAreaId=${it.id}&status=ACTIVE")
                    .retrieve()
                    .onStatus({ status -> status.isError}) { clientResponse ->
                        clientResponse.bodyToMono<String>().map {
                            RuntimeException("Error from Team catalog fetch teams in product area: ${clientResponse.statusCode()}, body: $it")
                        }
                    }
                    .bodyToMono<TeamResponse>()
                    .block()
                    ?: TeamResponse(emptyList())
            }
        } catch (e: Exception) {
            logger.error(e.message)
            emptyList()
        }
    }

    fun fetchAllMembersWithTeamData(): List<MemberWithTeamData> {
        val productAreas = if (useMockResponses()) {
            teamCatalogMock.loadMockProductAreas()
        } else {
            fetchAllProductAreas()
        }

        val teamsWithinProduct= if (useMockResponses()) {
            teamCatalogMock.loadMockTeamMembers(productAreas)
        } else {
            fetchAllTeams(productAreas)
        }

        if (teamsWithinProduct.isEmpty()) {
            logger.info("No teams were found. return empty list")
            return emptyList()
        }

        val membersWithTeamData = linkedMapOf<String, MemberWithTeamData>()

        teamsWithinProduct.forEach { teams ->
            teams.content.forEach { team ->
                team.members.forEach { member ->
                    val email = member.resource.email
                    if (email != null) {
                        val existingMember = membersWithTeamData[email]
                        if (existingMember != null) {
                            existingMember.teamName.add(team.name)
                            existingMember.teamId.add(team.id)
                        } else {
                            membersWithTeamData[email] = MemberWithTeamData(
                                navIdent = member.resource.navIdent,
                                fullName = member.resource.fullName,
                                email = email,
                                teamName = mutableListOf(team.name),
                                teamId = mutableListOf(team.id)
                            )
                        }
                    }
                }
            }
        }
        return membersWithTeamData.values.toList()
    }
}
