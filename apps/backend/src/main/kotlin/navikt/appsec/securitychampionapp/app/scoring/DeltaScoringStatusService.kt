package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.app.jobs.ScoringJobLockKeys
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaSyncOutcome
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

const val INTERRUPTED_SYNC_SUMMARY = "Sync was interrupted before it finished"

@Service
class DeltaScoringStatusService(
    private val repository: DeltaScoringStatusRepository,
    private val jobLock: PostgresJobLock,
    @Value($$"${delta.scoring.enabled:false}") private val enabled: Boolean,
) {
    fun status(): DeltaSyncStatusView {
        val record = repository.find()
        val interrupted = record?.outcome == DeltaSyncOutcome.RUNNING && !jobLock.isLocked(ScoringJobLockKeys.DELTA)
        return DeltaSyncStatusView(
            enabled = enabled,
            lastAttemptAt = record?.lastAttemptAt,
            lastSuccessAt = record?.lastSuccessAt,
            outcome = if (interrupted) DeltaSyncOutcome.FAILED.name else record?.outcome?.name,
            eventsScanned = record?.eventsScanned ?: 0,
            creditsAwarded = record?.creditsAwarded ?: 0,
            duplicateCredits = record?.duplicateCredits ?: 0,
            unmatchedRegistrations = record?.unmatchedRegistrations ?: 0,
            failedEvents = record?.failedEvents ?: 0,
            failureSummary = if (interrupted) INTERRUPTED_SYNC_SUMMARY else record?.failureSummary,
        )
    }
}
