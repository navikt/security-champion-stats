package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.app.jobs.ScoringJobLockKeys
import navikt.appsec.securitychampionapp.integrations.postgress.GitHubScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

data class GitHubSyncStatusView(
    val enabled: Boolean,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val outcome: String?,
    val contributionsScanned: Int,
    val creditsAwarded: Int,
    val duplicateCredits: Int,
    val unmappedAuthors: Int,
    val failureSummary: String?,
)

@Service
class GitHubScoringStatusService(
    private val repository: GitHubScoringStatusRepository,
    private val jobLock: PostgresJobLock,
    @Value($$"${github.scoring.enabled:false}") private val enabled: Boolean,
) {
    fun status(): GitHubSyncStatusView {
        val status = repository.find(enabled)
        return if (status.outcome == "RUNNING" && !jobLock.isLocked(ScoringJobLockKeys.GITHUB)) {
            status.copy(outcome = "FAILED", failureSummary = INTERRUPTED_SYNC_SUMMARY)
        } else {
            status
        }
    }
}
