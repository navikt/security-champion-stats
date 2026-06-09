package navikt.appsec.securitychampionapp.integrations.teamCatalog

import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.MemberWithTeamData
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.ProductAreaResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.ResourceResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogTeam
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class TeamCatalog(
    private val externalServiceWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(TeamCatalog::class.java)

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

    private fun fetchAllTeams(): List<TeamResponse> {
        val products = fetchAllProductAreas()
        if (products.content.isEmpty()) {
            logger.info("No products were found, returning empty list")
            return emptyList()
        }

        return try {
            products.content.map {
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

    fun fetchMembersWithRole(): List<MemberWithTeamData> {
        val teamsWithinProduct = fetchAllTeams()
        if (teamsWithinProduct.isEmpty()) {
            logger.info("No teams were found. return empty list")
            return emptyList()
        }

        val securityChamps = mutableListOf<MemberWithTeamData>()

        teamsWithinProduct.forEach { teams ->
            teams.content.forEach { team ->
                team.members.forEach { member ->
                    if (member.roles.contains("SECURITY_CHAMPION")) {
                        if(securityChamps.any { champ -> champ.email == member.resource.email}) {
                            securityChamps.forEach { champ ->
                                if(champ.email == member.resource.email) {
                                    champ.teamName.add(team.name)
                                    champ.teamId.add(team.id)
                                }
                            }
                        }
                    } else {
                            securityChamps.add(MemberWithTeamData(
                                navIdent = member.resource.navIdent,
                                fullName = member.resource.fullName,
                                email = member.resource.email ?: "unknown",
                                teamName = mutableListOf(team.name),
                                teamId = mutableListOf(team.id)
                            ))
                        }
                    }
                }
            }
        return securityChamps
    }
}