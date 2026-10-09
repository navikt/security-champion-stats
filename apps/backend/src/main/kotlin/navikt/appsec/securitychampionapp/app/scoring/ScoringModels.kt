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

data class ScoreHistorySeason(
    val id: UUID,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
)

data class ScoreBreakdown(
    val slack: Long,
    val deltaRegistration: Long,
    val githubCommit: Long,
    val githubPullRequest: Long,
    val securityEvent: Long,
    val adjustments: Long,
    val ruleChanges: Long = 0,
)

data class ScoreSummary(
    val points: Long,
    val tier: String,
    val rank: Int?,
    val breakdown: ScoreBreakdown,
    val seasons: List<ScoreHistorySeason>,
)

data class ParticipantScoreBreakdown(
    val slack: Long,
    val deltaRegistration: Long,
    val githubCommit: Long,
    val githubPullRequest: Long,
    val securityEvent: Long,
    val adjustments: Long,
)

data class ParticipantScoreSummary(
    val points: Long,
    val tier: String,
    val rank: Int?,
    val breakdown: ParticipantScoreBreakdown,
    val seasons: List<ScoreHistorySeason>,
)

data class ScoreHistoryRecord(
    val id: String,
    val type: String,
    val recordedAt: Instant,
    val activityAt: Instant?,
    val seasonId: UUID?,
    val creditType: ActivityCreditType?,
    val points: Int?,
    val displayName: String?,
    val sourceReference: String?,
    val creditId: String?,
    val linkedCreditId: String?,
    val reason: String?,
    val adminName: String?,
    val revokedAt: Instant?,
    val membershipAction: String?,
    val membershipStatusBefore: String?,
    val membershipStatusAfter: String?,
    val membershipReason: String?,
    val tieIndex: Long,
)

data class ScoreHistoryCursor(
    val recordedAt: Instant,
    val tieIndex: Long,
)

data class ParticipantScoreHistoryEntry(
    val kind: String,
    val occurredAt: Instant,
    val creditType: ActivityCreditType?,
    val points: Int?,
    val displayName: String?,
    val action: String?,
    val membershipStatusBefore: String?,
    val membershipStatusAfter: String?,
)

data class AdminScoreHistoryEntry(
    val id: String,
    val kind: String,
    val recordedAt: Instant,
    val activityAt: Instant?,
    val creditType: ActivityCreditType?,
    val points: Int?,
    val displayName: String?,
    val sourceRef: String?,
    val creditId: String?,
    val seasonId: UUID?,
    val reason: String?,
    val adminName: String?,
    val linkedCreditId: String?,
    val revokedAt: Instant?,
    val action: String?,
    val membershipStatusBefore: String?,
    val membershipStatusAfter: String?,
    val membershipReason: String?,
    val ruleChange: Boolean,
)

data class ScoreHistoryPage<T>(
    val entries: List<T>,
    val nextCursor: String?,
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
