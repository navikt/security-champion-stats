package navikt.appsec.securitychampionapp.integrations.github

enum class GitHubFailure(val summary: String) {
    CONFIGURATION("GitHub App configuration is incomplete or invalid"),
    TOKEN("GitHub App installation token could not be obtained"),
    ACCESS("GitHub access denied; check installation repository and Members read permissions"),
    RATE_LIMIT("GitHub API rate limit reached; wait for the reset or retry-after period before retrying"),
    API("GitHub API request failed; retry after checking API availability and rate limits"),
    RESPONSE("GitHub returned incomplete or invalid contribution data"),
    IDENTITY("GitHub organization SAML identities are unavailable or ambiguous; no credits were awarded"),
}

class GitHubIntegrationException(
    val failure: GitHubFailure,
    val reason: String? = null,
) : RuntimeException(failure.summary)
