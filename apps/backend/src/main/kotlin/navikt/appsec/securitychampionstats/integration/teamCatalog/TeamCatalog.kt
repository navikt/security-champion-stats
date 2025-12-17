package navikt.appsec.securitychampionstats.integration.teamCatalog

import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.ProductAreaResponse
import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.ResourceResponse
import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.TeamResponse
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

    fun fetchMembersWithRole(): List<ResourceResponse> {
        val teamsWithinProduct = fetchAllTeams()
        if (teamsWithinProduct.isEmpty()) {
            logger.info("No teams were found. return empty list")
            return emptyList()
        }

        val securityChamps = mutableListOf<ResourceResponse>()

        teamsWithinProduct.forEach { teams ->
            teams.content.forEach { team ->
                team.members.forEach { member ->
                    if (member.roles.contains("SECURITY_CHAMPION") && !securityChamps.contains(member.resource))  {
                        securityChamps.add(member.resource)
                    }
                }
            }
        }
        return securityChamps
    }
}