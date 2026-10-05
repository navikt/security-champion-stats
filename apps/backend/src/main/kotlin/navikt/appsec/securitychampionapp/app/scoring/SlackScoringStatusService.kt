package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.postgress.SlackScoringStatusRepository
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
) {
    fun status(): SlackSyncStatusView {
        val record = repository.find()
        return SlackSyncStatusView(
            enabled = true,
            lastAttemptAt = record?.lastAttemptAt,
            lastSuccessAt = record?.lastSuccessAt,
            outcome = record?.outcome?.name,
            messagesScanned = record?.messagesScanned ?: 0,
            creditsAwarded = record?.creditsAwarded ?: 0,
            duplicateCredits = record?.duplicateCredits ?: 0,
            unmappedAuthors = record?.unmappedAuthors ?: 0,
            failureSummary = record?.failureSummary,
        )
    }
}
