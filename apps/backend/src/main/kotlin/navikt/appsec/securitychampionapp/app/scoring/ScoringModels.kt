package navikt.appsec.securitychampionapp.app.scoring

import java.time.LocalDate
import java.util.UUID

enum class ActivityCreditType(val points: Int) {
    SLACK_WEEK(1),
    DELTA_REGISTRATION(1),
    GITHUB_COMMIT(1),
    GITHUB_PULL_REQUEST(3),
    SECURITY_EVENT_CONTRIBUTION(3),
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
)

data class OwnSeasonScore(
    val season: SeasonSummary,
    val points: Long,
    val level: String,
    val rank: Int?,
)

data class ActivityCredit(
    val id: UUID,
    val creditType: ActivityCreditType,
    val sourceReference: String,
    val points: Int,
    val seasonStartsOn: LocalDate,
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
