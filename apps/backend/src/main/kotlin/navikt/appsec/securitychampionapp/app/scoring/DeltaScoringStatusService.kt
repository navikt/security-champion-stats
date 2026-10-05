package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.postgress.DeltaScoringStatusRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class DeltaScoringStatusService(
    private val repository: DeltaScoringStatusRepository,
    @Value($$"${delta.scoring.enabled:false}") private val enabled: Boolean,
) {
    fun status(): DeltaSyncStatusView {
        val record = repository.find()
        return DeltaSyncStatusView(
            enabled = enabled,
            lastAttemptAt = record?.lastAttemptAt,
            lastSuccessAt = record?.lastSuccessAt,
            outcome = record?.outcome?.name,
            eventsScanned = record?.eventsScanned ?: 0,
            creditsAwarded = record?.creditsAwarded ?: 0,
            duplicateCredits = record?.duplicateCredits ?: 0,
            unmatchedRegistrations = record?.unmatchedRegistrations ?: 0,
            failedEvents = record?.failedEvents ?: 0,
            failureSummary = record?.failureSummary,
        )
    }
}
