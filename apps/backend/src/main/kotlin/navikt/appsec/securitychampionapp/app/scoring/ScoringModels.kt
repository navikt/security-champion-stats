package navikt.appsec.securitychampionapp.app.scoring

import java.time.LocalDate
import java.time.Instant
import java.util.UUID

enum class ActivityCreditType {
    SLACK_WEEK,
    DELTA_REGISTRATION,
    GITHUB_COMMIT,
    GITHUB_PULL_REQUEST,
    SECURITY_EVENT_CONTRIBUTION,
}

data class SeasonSummary(
    val id: UUID,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val nextResetDate: LocalDate,
)

data class ParticipantSeasonScore(
    val participantId: UUID,
    val fullName: String,
    val email: String,
    val active: Boolean,
    val points: Long,
)

data class AdminScoringOverview(
    val season: SeasonSummary,
    val today: LocalDate,
    val participants: List<AdminParticipantScore>,
    val configuration: ScoringConfiguration,
)

data class AdminParticipantScore(
    val participantId: UUID,
    val fullName: String,
    val email: String,
    val active: Boolean,
    val points: Long,
    val level: String,
)

data class RecognitionEntry(
    val fullName: String,
    val rank: Int,
)

data class LeaderboardEntry(
    val fullName: String,
    val rank: Int,
    val points: Long,
    val level: String,
    val isCurrentUser: Boolean = false,
)

data class OwnSeasonScore(
    val season: SeasonSummary,
    val points: Long,
    val level: String,
    val rank: Int?,
    val tiers: List<ScoringTier>,
)

data class ActivityCredit(
    val id: UUID,
    val creditType: ActivityCreditType,
    val sourceReference: String,
    val points: Int,
    val seasonStartsOn: LocalDate,
)

enum class ScoringHistoryEntryType {
    CREDIT,
    ADJUSTMENT,
    SCORING_RULE_CHANGE,
}

data class ScoringHistoryEntry(
    val id: UUID,
    val type: ScoringHistoryEntryType,
    val recordedAt: Instant,
    val activityAt: Instant?,
    val seasonId: UUID,
    val seasonStartsOn: LocalDate,
    val seasonEndsOn: LocalDate?,
    val points: Int,
    val creditType: ActivityCreditType?,
    val sourceReference: String?,
    val sourceCreditId: UUID?,
    val reason: String?,
    val actorNavNoEmail: String?,
    val revokedAt: Instant?,
)

data class ParticipantScoringHistory(
    val currentSeasonId: UUID,
    val seasons: List<ParticipantScoringSeason>,
    val entries: List<ScoringHistoryEntry>,
)

data class ParticipantScoringSeason(
    val id: UUID,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val points: Long,
    val creditPoints: Map<ActivityCreditType, Long>,
    val adjustmentPoints: Long,
    val scoringRulePoints: Long,
)

data class PointAdjustment(
    val id: UUID,
    val participantId: UUID,
    val seasonId: UUID,
    val pointsDelta: Int,
    val scoreBefore: Long,
    val scoreAfter: Long,
)

enum class CreditAwardResult {
    AWARDED,
    DUPLICATE,
    PARTICIPANT_INACTIVE_OR_MISSING,
}

class InvalidScoringRequestException(message: String) : RuntimeException(message)

class ScoringTargetNotFoundException : RuntimeException("The participant does not exist")

class SourceCreditNotFoundException : RuntimeException("The source credit does not exist for participant")
