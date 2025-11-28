package navikt.appsec.securitychampionstats.integration.teamCatalog

import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.Member
import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.Team
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class TeamCatalog(
    private val externalServiceWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(TeamCatalog::class.java)

    private fun fetchAllTeams(): List<Team> {
        val result =  externalServiceWebClient
            .get()
            .uri("/team?status=ACTIVE")
            .retrieve()
            .bodyToMono<List<Team>>()
            .block()
            ?: throw IllegalStateException("Something went wrong then fetching teams")
        return result
    }

    fun fetchMembersWithRole(role: String): List<Member> {
        return try {
            val teamsWithRole = fetchAllTeams().map { team ->
                team.copy(
                    members = team.members.filter { member ->
                        member?.roles?.contains(role) ?: false
                    }
                )
            }
            val securityChampions = mutableListOf<Member>()
            teamsWithRole.forEach { team ->
                team.members.forEach {
                    if (it != null)
                    securityChampions.add(it)
                }
            }
            securityChampions.toList()
        } catch (e: Exception) {
            logger.error("Failed to fetch members with $role, error: $e")
            emptyList()
        }
    }




}