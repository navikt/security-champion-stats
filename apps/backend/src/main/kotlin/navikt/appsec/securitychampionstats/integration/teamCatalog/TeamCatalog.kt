package navikt.appsec.securitychampionstats.integration.teamCatalog

import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.ProductAreaResponse
import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.ResourceResponse
import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.TeamResponse
import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.TeamRole
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

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
                    clientResponse.bodyToMono(String::class.java).map {
                        RuntimeException("Error from Team catalog fetch product area: ${clientResponse.statusCode()}, body: $it")
                    }
                }
                .bodyToMono<ProductAreaResponse>()
                //.timeout(Duration.ofSeconds(200))
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
        logger.info("data from product adre $products")
        return try {
            products.content.map {
                externalServiceWebClient
                    .get()
                    .uri("/team?productAreaId=${it.id}&status=ACTIVE")
                    .retrieve()
                    .onStatus({ status -> status.isError}) { clientResponse ->
                        clientResponse.bodyToMono(String::class.java).map {
                            RuntimeException("Error from Team catalog fetch teams in product area: ${clientResponse.statusCode()}, body: $it")
                        }
                    }
                    .bodyToMono<TeamResponse>()
                    .block()
                    ?: TeamResponse("", "", emptyList())
            }
        } catch (e: Exception) {
            logger.error(e.message)
            emptyList()
        }
    }

    fun fetchMembersWithRole(): List<ResourceResponse> {
        val teamsWithRole = fetchAllTeams()
        if (teamsWithRole.isEmpty()) {
            logger.info("No teams were found. return empty list")
            return emptyList()
        }

        val securityChamps = mutableListOf<ResourceResponse>()

        teamsWithRole.forEach { teams ->
            teams.naisTeam?.forEach {
                if (it.roles.contains(TeamRole.SECURITY_CHAMPION)) {
                    securityChamps.add(it.resource ?: ResourceResponse(
                        "",
                        "",
                        ""
                    ))
                }
            }
        }
        return securityChamps
    }
}