package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import org.springframework.stereotype.Service
import java.util.UUID

data class ScoringTier(val name: String, val points: Int)
data class ActivityPoints(val creditType: ActivityCreditType, val points: Int)
data class ScoringConfiguration(
    val version: Long,
    val tiers: List<ScoringTier>,
    val activities: List<ActivityPoints>,
) {
    fun levelFor(points: Long): String =
        (tiers.lastOrNull { points >= it.points } ?: tiers.first()).name
}

data class ScoringConfigurationRequest(
    val expectedVersion: Long,
    val tiers: List<ScoringTier>,
    val activities: List<ActivityPoints>,
    val applyRetroactively: Boolean = false,
    val reason: String,
    val previewToken: String? = null,
)

data class ScoringImpact(
    val participantId: UUID,
    val fullName: String,
    val pointsBefore: Long,
    val pointsAfter: Long,
    val levelBefore: String,
    val levelAfter: String,
)

data class ScoringConfigurationPreview(
    val token: String,
    val season: SeasonSummary,
    val affectedCredits: Int,
    val pointsDelta: Long,
    val participants: List<ScoringImpact>,
)

class StaleScoringConfigurationException :
    RuntimeException("Scoring changed since the preview. Reload the configuration and preview again.")

@Service
class ScoringConfigurationService(
    private val ledger: ScoringLedger,
    private val auditService: ProgramAuditService? = null,
) {
    fun configuration(): ScoringConfiguration = ledger.configuration()

    fun preview(request: ScoringConfigurationRequest): ScoringConfigurationPreview =
        ledger.previewConfiguration(validate(request))

    fun save(request: ScoringConfigurationRequest, actor: String): ScoringConfiguration {
        if (actor.isBlank()) throw InvalidScoringRequestException("An administrator identity is required")
        val normalized = validate(request)
        if (normalized.previewToken.isNullOrBlank()) {
            throw InvalidScoringRequestException("Preview and confirmation are required")
        }
        val result = ledger.saveConfiguration(normalized, actor)
        auditService?.record(
            action = "SCORING_CONFIGURATION_UPDATED",
            outcome = AuditOutcome.SUCCEEDED,
            actorNavNoEmail = actor,
            details = mapOf(
                "version" to result.version,
                "applyRetroactively" to request.applyRetroactively,
                "reason" to normalized.reason,
                "tiers" to result.tiers,
                "activities" to result.activities,
            ),
        )
        return result
    }

    private fun validate(request: ScoringConfigurationRequest): ScoringConfigurationRequest {
        val tiers = request.tiers.map { it.copy(name = it.name.trim()) }.sortedBy { it.points }
        if (tiers.isEmpty() || tiers.size > 50 || tiers.first().points != 0 ||
            tiers.any { it.points < 0 || it.name.isBlank() || it.name.length > 80 } ||
            tiers.map { it.points }.distinct().size != tiers.size ||
            tiers.map { it.name.lowercase() }.distinct().size != tiers.size
        ) {
            throw InvalidScoringRequestException(
                "Use 1-50 uniquely named tiers with distinct non-negative thresholds, starting at zero",
            )
        }
        if (request.activities.size != ActivityCreditType.entries.size ||
            request.activities.map { it.creditType }.toSet() != ActivityCreditType.entries.toSet() ||
            request.activities.any { it.points < 0 }
        ) {
            throw InvalidScoringRequestException("Provide a non-negative whole-number point value for every activity")
        }
        if (request.reason.isBlank() || request.reason.length > 1000) {
            throw InvalidScoringRequestException("Provide a reason of up to 1000 characters")
        }
        return request.copy(
            tiers = tiers,
            activities = request.activities.sortedBy { it.creditType.ordinal },
            reason = request.reason.trim(),
        )
    }
}
