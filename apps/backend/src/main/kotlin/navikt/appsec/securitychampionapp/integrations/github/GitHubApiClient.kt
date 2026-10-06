package navikt.appsec.securitychampionapp.integrations.github

import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import org.springframework.core.codec.DecodingException
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.format.DateTimeParseException

private const val REPOSITORY_PATH = "/repos/navikt/security-playbook"

class GitHubApiClient(
    private val client: WebClient,
    private val tokens: GitHubAppTokenProvider,
) : GitHubContributionSource {
    override fun identities(): List<GitHubIdentity> {
        val identities = mutableListOf<GitHubIdentity>()
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()
        do {
            val response = request {
                client.post().uri("/graphql").bodyValue(
                    mapOf(
                        "query" to IDENTITY_QUERY,
                        "variables" to mapOf("cursor" to cursor),
                    ),
                )
            }
            if (response.has("errors")) throw GitHubIntegrationException(GitHubFailure.IDENTITY)
            val connection = response.path("data").path("organization").path("samlIdentityProvider")
                .path("externalIdentities")
            val nodes = connection.path("nodes")
            if (!nodes.isArray) throw GitHubIntegrationException(GitHubFailure.IDENTITY)
            nodes.forEach { node ->
                val user = node.path("user")
                val saml = node.path("samlIdentity")
                if (!user.isNull && !user.isMissingNode && !saml.isNull && !saml.isMissingNode) {
                    val email = requiredText(saml.path("nameId")).trim()
                    if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                        throw GitHubIntegrationException(GitHubFailure.IDENTITY)
                    }
                    identities += GitHubIdentity(
                        accountId = positiveId(user.path("databaseId")),
                        login = requiredText(user.path("login")),
                        email = email.lowercase(),
                    )
                }
            }
            val pageInfo = connection.path("pageInfo")
            if (!pageInfo.path("hasNextPage").isBoolean) throw GitHubIntegrationException(GitHubFailure.IDENTITY)
            cursor = if (pageInfo.path("hasNextPage").asBoolean()) {
                requiredText(pageInfo.path("endCursor")).also {
                    if (!seenCursors.add(it)) throw GitHubIntegrationException(GitHubFailure.IDENTITY)
                }
            } else {
                null
            }
        } while (cursor != null)
        if (identities.groupBy { it.accountId }.any { it.value.size > 1 } ||
            identities.groupBy { it.email }.any { it.value.size > 1 }
        ) {
            throw GitHubIntegrationException(GitHubFailure.IDENTITY)
        }
        return identities
    }

    override fun contributions(since: Instant, until: Instant): List<GitHubContribution> {
        val repository = request { client.get().uri(REPOSITORY_PATH) }
        val branch = requiredText(repository.path("default_branch"))
        val contributions = mutableListOf<GitHubContribution>()
        paged("$REPOSITORY_PATH/pulls", mapOf("state" to "closed", "base" to branch)).forEach { pr ->
            if (pr.path("merged_at").isNull) return@forEach
            if (requiredText(pr.path("base").path("ref")) != branch) return@forEach
            val mergedAt = timestamp(pr.path("merged_at"))
            if (mergedAt.isBefore(since) || mergedAt.isAfter(until)) return@forEach
            val author = pr.path("user")
            if (isHuman(author)) {
                contributions += GitHubContribution(
                    positiveId(author.path("id")),
                    ActivityCreditType.GITHUB_PULL_REQUEST,
                    "navikt/security-playbook:pr:${positiveId(pr.path("number"))}",
                    mergedAt,
                )
            }
        }
        paged(
            "$REPOSITORY_PATH/commits",
            mapOf("sha" to branch, "since" to since.toString(), "until" to until.toString()),
        ).forEach { commit ->
            val sha = requiredText(commit.path("sha"))
            if (!sha.matches(Regex("[a-fA-F0-9]{40}"))) throw GitHubIntegrationException(GitHubFailure.RESPONSE)
            val associated = paged("$REPOSITORY_PATH/commits/$sha/pulls")
            val includedInPr = associated.any { pr ->
                !pr.path("merged_at").isNull && !pr.path("merged_at").isMissingNode &&
                    requiredText(pr.path("base").path("ref")) == branch
            }
            val author = commit.path("author")
            val committer = commit.path("committer")
            val at = timestamp(commit.path("commit").path("committer").path("date"))
            if (!includedInPr && isHuman(author) && committer.path("type").asString("") != "Bot" &&
                !at.isBefore(since) && !at.isAfter(until)
            ) {
                contributions += GitHubContribution(
                    positiveId(author.path("id")),
                    ActivityCreditType.GITHUB_COMMIT,
                    "navikt/security-playbook:commit:${sha.lowercase()}",
                    at,
                )
            }
        }
        return contributions.distinctBy { it.key }
    }

    private fun paged(path: String, parameters: Map<String, String> = emptyMap()): List<JsonNode> {
        val nodes = mutableListOf<JsonNode>()
        var page = 1
        while (true) {
            val response = request {
                client.get().uri { uri ->
                    uri.path(path).queryParam("per_page", 100).queryParam("page", page).apply {
                        parameters.forEach { (key, value) -> queryParam(key, value) }
                    }.build()
                }
            }
            if (!response.isArray) throw GitHubIntegrationException(GitHubFailure.RESPONSE)
            nodes.addAll(response.toList())
            if (response.size() < 100) return nodes
            page++
        }
    }

    private fun request(build: () -> WebClient.RequestHeadersSpec<*>): JsonNode {
        repeat(2) { attempt ->
            val token = tokens.token()
            try {
                return build().headers { it.setBearerAuth(token) }
                    .retrieve()
                    .onStatus({ it.value() == 401 }) { Mono.error(ExpiredInstallationToken()) }
                    .onStatus({ it.value() == 403 || it.value() == 404 }) {
                        Mono.error(GitHubIntegrationException(GitHubFailure.ACCESS))
                    }
                    .onStatus({ it.isError }) { Mono.error(GitHubIntegrationException(GitHubFailure.API)) }
                    .bodyToMono<JsonNode>()
                    .block()
                    ?: throw GitHubIntegrationException(GitHubFailure.RESPONSE)
            } catch (_: ExpiredInstallationToken) {
                tokens.invalidate()
                if (attempt == 1) throw GitHubIntegrationException(GitHubFailure.ACCESS)
            } catch (_: WebClientRequestException) {
                throw GitHubIntegrationException(GitHubFailure.API)
            } catch (_: DecodingException) {
                throw GitHubIntegrationException(GitHubFailure.RESPONSE)
            }
        }
        throw GitHubIntegrationException(GitHubFailure.ACCESS)
    }

    private fun isHuman(user: JsonNode): Boolean = user.path("type").asString("") == "User"

    private fun positiveId(node: JsonNode): Long =
        node.asLong(0).takeIf { node.isIntegralNumber && it > 0 }
            ?: throw GitHubIntegrationException(GitHubFailure.RESPONSE)

    private fun requiredText(node: JsonNode): String =
        node.asString("").takeIf { node.isString && it.isNotBlank() }
            ?: throw GitHubIntegrationException(GitHubFailure.RESPONSE)

    private fun timestamp(node: JsonNode): Instant =
        try {
            Instant.parse(requiredText(node))
        } catch (_: DateTimeParseException) {
            throw GitHubIntegrationException(GitHubFailure.RESPONSE)
        }

    private class ExpiredInstallationToken : RuntimeException()

    private companion object {
        val IDENTITY_QUERY = """
            query ParticipantIdentities(${'$'}cursor: String) {
              organization(login: "navikt") {
                samlIdentityProvider {
                  externalIdentities(first: 100, after: ${'$'}cursor) {
                    nodes { user { databaseId login } samlIdentity { nameId } }
                    pageInfo { hasNextPage endCursor }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
