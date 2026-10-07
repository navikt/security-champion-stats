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
        val commits = paged(
            "$REPOSITORY_PATH/commits",
            mapOf("sha" to branch, "since" to since.toString(), "until" to until.toString()),
        ).filter { commit ->
            val at = timestamp(commit.path("commit").path("committer").path("date"))
            isHuman(commit.path("author")) && commit.path("committer").path("type").asString("") != "Bot" &&
                !at.isBefore(since) && !at.isAfter(until)
        }
        val shas = commits.map { commit ->
            val sha = requiredText(commit.path("sha"))
            if (!sha.matches(Regex("[a-fA-F0-9]{40}"))) throw GitHubIntegrationException(GitHubFailure.RESPONSE)
            sha
        }
        val includedInPr = mergedPrCommits(shas.distinct(), branch)
        commits.forEach { commit ->
            val sha = requiredText(commit.path("sha"))
            val author = commit.path("author")
            val at = timestamp(commit.path("commit").path("committer").path("date"))
            if (sha !in includedInPr) {
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

    private fun mergedPrCommits(shas: List<String>, branch: String): Set<String> {
        val included = mutableSetOf<String>()
        shas.chunked(50).forEach { batch ->
            var pending: Map<String, String?> = batch.associateWith { null }
            val seenCursors = batch.associateWith { mutableSetOf<String>() }
            while (pending.isNotEmpty()) {
                val entries = pending.entries.toList()
                val declarations = entries.indices.joinToString(", ") {
                    "${'$'}sha$it: GitObjectID!, ${'$'}cursor$it: String"
                }
                val fields = entries.indices.joinToString("\n") {
                    """
                        commit$it: object(oid: ${'$'}sha$it) {
                          ... on Commit {
                            associatedPullRequests(first: 100, after: ${'$'}cursor$it) {
                              nodes { mergedAt baseRefName }
                              pageInfo { hasNextPage endCursor }
                            }
                          }
                        }
                    """.trimIndent()
                }
                val variables = entries.flatMapIndexed { index, entry ->
                    listOf("sha$index" to entry.key, "cursor$index" to entry.value)
                }.toMap()
                val response = request {
                    client.post().uri("/graphql").bodyValue(
                        mapOf(
                            "query" to "query CommitAssociations($declarations) {" +
                                " repository(owner: \"navikt\", name: \"security-playbook\") { $fields } }",
                            "variables" to variables,
                        ),
                    )
                }
                if (response.has("errors")) throw GitHubIntegrationException(GitHubFailure.RESPONSE)
                val next = mutableMapOf<String, String?>()
                entries.forEachIndexed { index, entry ->
                    val connection = response.path("data").path("repository").path("commit$index")
                        .path("associatedPullRequests")
                    val nodes = connection.path("nodes")
                    val pageInfo = connection.path("pageInfo")
                    if (!nodes.isArray || !pageInfo.path("hasNextPage").isBoolean) {
                        throw GitHubIntegrationException(GitHubFailure.RESPONSE)
                    }
                    val merged = nodes.any { pr ->
                        val mergedAt = pr.path("mergedAt")
                        if (mergedAt.isMissingNode) throw GitHubIntegrationException(GitHubFailure.RESPONSE)
                        val target = requiredText(pr.path("baseRefName"))
                        if (!mergedAt.isNull) timestamp(mergedAt)
                        !mergedAt.isNull && target == branch
                    }
                    if (merged) {
                        included += entry.key
                    } else if (pageInfo.path("hasNextPage").asBoolean()) {
                        val cursor = requiredText(pageInfo.path("endCursor"))
                        if (!seenCursors.getValue(entry.key).add(cursor)) {
                            throw GitHubIntegrationException(GitHubFailure.RESPONSE)
                        }
                        next[entry.key] = cursor
                    }
                }
                pending = next
            }
        }
        return included
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
                    .onStatus({ it.value() == 403 || it.value() == 429 }) { response ->
                        val headers = response.headers().asHttpHeaders()
                        val rateLimited = response.statusCode().value() == 429 ||
                            headers.getFirst("X-RateLimit-Remaining") == "0" ||
                            headers.getFirst("Retry-After") != null
                        response.bodyToMono<JsonNode>()
                            .defaultIfEmpty(tools.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                            .flatMap { body ->
                                val message = body.path("message").asString("").lowercase()
                                val throttled = rateLimited || "rate limit" in message || "abuse detection" in message
                                val failure = if (throttled) {
                                    GitHubFailure.RATE_LIMIT
                                } else {
                                    GitHubFailure.ACCESS
                                }
                                Mono.error<Throwable>(GitHubIntegrationException(failure))
                            }
                    }
                    .onStatus({ it.value() == 404 }) {
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
