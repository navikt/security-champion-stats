package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.app.jobs.ScoringJobLockKeys
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.SlackScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.SlackSyncOutcome
import org.springframework.stereotype.Service
import java.time.Instant

data class SlackSyncStatusView(
    val enabled: Boolean,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val outcome: String?,
    val messagesScanned: Int,
    val creditsAwarded: Int,
    val duplicateCredits: Int,
    val unmappedAuthors: Int,
    val failureSummary: String?,
)

@Service
class SlackScoringStatusService(
    private val repository: SlackScoringStatusRepository,
    private val jobLock: PostgresJobLock,
) {
    fun status(): SlackSyncStatusView {
        val record = repository.find()
        val interrupted = record?.outcome == SlackSyncOutcome.RUNNING && !jobLock.isLocked(ScoringJobLockKeys.SLACK)
        return SlackSyncStatusView(
            enabled = true,
            lastAttemptAt = record?.lastAttemptAt,
            lastSuccessAt = record?.lastSuccessAt,
            outcome = if (interrupted) SlackSyncOutcome.FAILED.name else record?.outcome?.name,
            messagesScanned = record?.messagesScanned ?: 0,
            creditsAwarded = record?.creditsAwarded ?: 0,
            duplicateCredits = record?.duplicateCredits ?: 0,
            unmappedAuthors = record?.unmappedAuthors ?: 0,
            failureSummary = if (interrupted) INTERRUPTED_SYNC_SUMMARY else record?.failureSummary,
        )
    }
}
