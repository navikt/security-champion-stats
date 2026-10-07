package navikt.appsec.securitychampionapp.integrations.github

import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import java.time.Instant

interface GitHubContributionSource {
    fun identities(): List<GitHubIdentity>
    fun contributions(since: Instant, until: Instant): List<GitHubContribution>
}

data class GitHubIdentity(val accountId: Long, val login: String, val email: String)

data class GitHubContribution(
    val accountId: Long,
    val type: ActivityCreditType,
    val key: String,
    val occurredAt: Instant,
)
