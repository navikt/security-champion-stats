package navikt.appsec.securitychampionapp.app.scoring

import java.time.Instant

data class DeltaSyncStatusView(
    val enabled: Boolean,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val outcome: String?,
    val eventsScanned: Int,
    val creditsAwarded: Int,
    val duplicateCredits: Int,
    val unmatchedRegistrations: Int,
    val failedEvents: Int,
    val failureSummary: String?,
)
