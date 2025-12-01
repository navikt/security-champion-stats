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

    private fun fetchAllTeams(): TeamResponse {
        val result =  externalServiceWebClient
            .get()
            .uri("/team?status=ACTIVE")
            .retrieve()
            .bodyToMono<TeamResponse>()
            .block()
            ?: throw IllegalStateException("Something went wrong then fetching teams")
        return result
    }

    fun fetchMembersWithRole(role: String): List<ResourceResponse> {
        return try {
            val teamsWithRole = fetchAllTeams().naisTeam.filter { member ->
                if (member != null) {
                    member.roles.any { it?.name == role }
                } else {
                    false
                }
            }
            teamsWithRole.mapNotNull { if (it != null) it.resource!! else null }
        } catch (e: Exception) {
            logger.error("Failed to fetch members with $role, error: $e")
            emptyList()
        }
    }
}