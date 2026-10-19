package navikt.appsec.securitychampionapp.integrations.graph

import navikt.appsec.securitychampionapp.app.scoring.AdminGroupMembers
import navikt.appsec.securitychampionapp.app.scoring.AdminGroupUnavailableException
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.Locale

class GraphAdminGroupMembers(
    private val client: WebClient,
    private val tokenClient: WebClient,
    private val baseUrl: String,
    private val tokenEndpoint: String,
    private val groupId: String,
    private val clock: Clock,
) : AdminGroupMembers {
    private data class Snapshot(val emails: Set<String>, val expiresAt: Instant)
    private var snapshot: Snapshot? = null

    @Synchronized
    override fun emails(): Set<String> {
        snapshot?.takeIf { clock.instant().isBefore(it.expiresAt) }?.let { return it.emails }
        if (tokenEndpoint.isBlank() || groupId.isBlank()) throw AdminGroupUnavailableException()
        val emails = try {
            fetchMembers(acquireToken())
        } catch (_: WebClientRequestException) {
            throw AdminGroupUnavailableException()
        } catch (_: DecodingException) {
            throw AdminGroupUnavailableException()
        }
        snapshot = Snapshot(emails, clock.instant().plusSeconds(300))
        return emails
    }

    private fun acquireToken(): String =
        tokenClient.post()
            .uri(tokenEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("identity_provider", "entra_id")
                    .with("target", "https://graph.microsoft.com/.default"),
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { Mono.error(AdminGroupUnavailableException()) }
            .bodyToMono<JsonNode>()
            .block()
            ?.path("access_token")?.requiredText()
            ?: throw AdminGroupUnavailableException()

    private fun fetchMembers(token: String): Set<String> {
        val membersUri = URI.create("${baseUrl.trimEnd('/')}/groups/$groupId/members")
        var next: URI? = URI.create("$membersUri?\$select=id,userPrincipalName&\$top=999")
        val visited = mutableSetOf<URI>()
        val emails = mutableSetOf<String>()
        while (next != null) {
            val uri = next
            // Pagination must not send the Graph bearer token to a different resource or host.
            if (uri.scheme != membersUri.scheme || uri.rawAuthority != membersUri.rawAuthority ||
                uri.path != membersUri.path || uri.fragment != null || !visited.add(uri)
            ) throw AdminGroupUnavailableException()
            val page = client.get().uri(uri)
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .onStatus(HttpStatusCode::isError) { Mono.error(AdminGroupUnavailableException()) }
                .bodyToMono<JsonNode>().block() ?: throw AdminGroupUnavailableException()
            val members = page.path("value")
            if (!members.isArray) throw AdminGroupUnavailableException()
            members.forEach { member ->
                val type = member.path("@odata.type").requiredText()
                if (type == "#microsoft.graph.user") {
                    val email = member.path("userPrincipalName").requiredText()
                    emails += email.lowercase(Locale.ROOT)
                }
            }
            val link = page.path("@odata.nextLink")
            next = if (link.isMissingNode || link.isNull) null else {
                val value = link.requiredText()
                try {
                    URI(value)
                } catch (_: java.net.URISyntaxException) {
                    throw AdminGroupUnavailableException()
                }
            }
        }
        return emails.toSet()
    }

    private fun JsonNode.requiredText(): String =
        takeIf { it.isString }?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw AdminGroupUnavailableException()
}
