package navikt.appsec.securitychampionstats.connectors

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.statement.HttpResponse
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import navikt.appsec.securitychampionstats.models.TeamkatalogResourceType
import io.ktor.serialization.kotlinx.json.json
import navikt.appsec.securitychampionstats.models.ResourceGroup
import navikt.appsec.securitychampionstats.models.ResourceMemberWithGroup
import org.slf4j.LoggerFactory

class Teamkatalog(
    private val endpoint: String,
) {
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    private val logger = LoggerFactory.getLogger(Teamkatalog::class.java)

    private suspend fun getResource(resourceType: TeamkatalogResourceType): ResourceGroup {
        val getResponse: HttpResponse = client.get("$endpoint/$resourceType") {
            headers {
                append(HttpHeaders.Accept, "application/json")
                append("Nav-Consumer-Id", "Security-champion-stats")
            }
        }
        return getResponse.body()
    }

    private suspend fun getAllMemberGroups(): List<ResourceGroup> {
        val responses = TeamkatalogResourceType.entries.map { type -> getResource(type) }
        return responses
    }

    suspend fun getMembersWithRile(role: String): ResourceMemberWithGroup {
        val groups = getAllMemberGroups()
        val allMembers = groups.flatMap { group ->
            group.members.map { member ->
                ResourceMemberWithGroup(group, member)
            }
        }
        val membersWithARole = allMembers.filter { it.members.roles.contains(role) }
        membersWithARole.forEach { roleMember ->
            roleMember.members.members.forEach { member ->
                if (member.fullname == "Paulius Deveika") {
                    logger.info("Returning $roleMember")
                    return roleMember
                }
            }
        }
        logger.info("Didnt find all members with role $role")
        return membersWithARole.random()
    }
}

