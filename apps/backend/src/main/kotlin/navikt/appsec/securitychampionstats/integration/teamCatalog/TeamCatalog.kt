package navikt.appsec.securitychampionstats.integration.teamCatalog

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

    private fun fetchAllTeams(): List<TeamResponse> {
        val result =  externalServiceWebClient
            .get()
            .uri("/team?status=ACTIVE")
            .retrieve()
            .bodyToMono<List<TeamResponse>>()
            .block()
            ?: throw IllegalStateException("Something went wrong then fetching teams")
        return result
    }

    fun fetchMembersWithRole(role: String): List<ResourceResponse> {
        return try {
            val teamsWithRole = fetchAllTeams().map { team ->
                team.copy(
                    naisTeam = team.naisTeam.filter { member ->
                        member.roles.any { it.name == role }
                    }
                )
            }
            val securityChampions = mutableListOf<ResourceResponse>()
            teamsWithRole.forEach { team ->
                team.naisTeam.forEach {
                    securityChampions.add(it.resource)
                }
            }
            securityChampions.toList()
        } catch (e: Exception) {
            logger.error("Failed to fetch members with $role, error: $e")
            emptyList()
        }
    }
}