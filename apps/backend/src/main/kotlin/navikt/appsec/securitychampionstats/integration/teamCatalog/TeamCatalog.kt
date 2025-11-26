package navikt.appsec.securitychampionstats.integration.teamCatalog

import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.Member
import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.Teams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class TeamCatalog(
    private val externalServiceWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(TeamCatalog::class.java)

    private fun fetchAllTeams(): Teams {
        val result =  externalServiceWebClient
            .get()
            .uri("/team?status=ACTIVE")
            .retrieve()
            .bodyToMono<Teams>()
            .block()
            ?: throw IllegalStateException("Something went wrong then fetching teams")
        return result
    }

    fun fetchMembersWithRole(role: String): List<Member> {
        return try {
            val teamsWithRole = fetchAllTeams().teams.map { team ->
                team.copy(
                    members = team.members.filter { member ->
                        role in member.roles
                    }
                )
            }
            val securityChampions = mutableListOf<Member>()
            teamsWithRole.forEach { team ->
                team.members.forEach {
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