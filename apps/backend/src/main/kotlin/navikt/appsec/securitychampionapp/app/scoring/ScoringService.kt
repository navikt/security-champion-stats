package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.ScoringRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private val PROGRAM_TIME_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

@Service
class ScoringService(
    private val repository: ScoringRepository,
    private val auditService: ProgramAuditService? = null,
) {
    fun adminOverview(): AdminScoringOverview {
        val season = repository.currentSeason()
        val scores = repository.scoresForSeason(season.id)
        return AdminScoringOverview(
            season = season,
            today = LocalDate.now(PROGRAM_TIME_ZONE),
            participants = scores.map {
                AdminParticipantScore(
                    participantId = it.participantId,
                    fullName = it.fullName,
                    email = it.email,
                    active = it.active,
                    points = it.points,
                    level = levelFor(it.points),
                )
            },
        )
    }

    fun recognition(): List<RecognitionEntry> =
        ranked(repository.scoresForCurrentSeason(activeOnly = true))
            .filter { it.points > 0 && it.rank <= 5 }
            .map { RecognitionEntry(it.fullName, it.rank) }

    fun leaderboard(): List<LeaderboardEntry> =
        ranked(repository.scoresForCurrentSeason(activeOnly = true))
            .filter { it.points > 0 }
            .map { LeaderboardEntry(it.fullName, it.rank, it.points, levelFor(it.points)) }

    fun ownScore(participantId: UUID): OwnSeasonScore {
        val season = repository.currentSeason()
        val points = repository.scoreForParticipant(participantId, season.id)
        val rank = if (points > 0) {
            ranked(repository.scoresForSeason(season.id, activeOnly = true))
                .firstOrNull { it.participantId == participantId }
                ?.rank
        } else {
            null
        }
        return OwnSeasonScore(season, points, levelFor(points), rank)
    }

    fun creditsForParticipant(participantId: UUID): List<ActivityCredit> {
        if (!repository.participantExists(participantId)) throw ScoringTargetNotFoundException()
        return repository.creditsForParticipant(participantId)
    }

    fun awardCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID? = null,
    ): CreditAwardResult {
        if (uniquenessKey.isBlank() || sourceReference.isBlank()) {
            throw InvalidScoringRequestException("A source reference and uniqueness key are required")
        }
        val result = repository.awardCredit(
            participantId,
            creditType,
            uniquenessKey,
            sourceReference,
            auditCorrelationId,
        )
        recordAward(participantId, creditType, sourceReference, auditCorrelationId, result)
        return result
    }

    fun awardGitHubCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID?,
        activityAt: Instant,
        expectedSeasonId: UUID,
    ): CreditAwardResult {
        if (creditType !in setOf(ActivityCreditType.GITHUB_COMMIT, ActivityCreditType.GITHUB_PULL_REQUEST) ||
            uniquenessKey.isBlank() || sourceReference.isBlank()
        ) {
            throw InvalidScoringRequestException("A qualifying GitHub activity and source identity are required")
        }
        val result = repository.awardGitHubCredit(
            participantId, creditType, uniquenessKey, sourceReference, auditCorrelationId, activityAt, expectedSeasonId,
        )
        recordAward(participantId, creditType, sourceReference, auditCorrelationId, result)
        return result
    }

    private fun recordAward(
        participantId: UUID,
        creditType: ActivityCreditType,
        sourceReference: String,
        auditCorrelationId: UUID?,
        result: CreditAwardResult,
    ) {
        if (result == CreditAwardResult.AWARDED) {
            auditService?.record(
                action = "CREDIT_AWARDED",
                outcome = AuditOutcome.SUCCEEDED,
                targetParticipantId = participantId,
                correlationId = auditCorrelationId,
                details = mapOf(
                    "creditType" to creditType.name,
                    "points" to creditType.points,
                    "sourceReference" to sourceReference,
                ),
            )
        }
    }

    fun addAdjustment(
        participantId: UUID,
        pointsDelta: Int,
        reason: String,
        actorNavNoEmail: String,
        sourceCreditId: UUID?,
    ): PointAdjustment {
        if (pointsDelta == 0) throw InvalidScoringRequestException("The adjustment must not be zero")
        if (reason.isBlank()) throw InvalidScoringRequestException("A reason is required")
        if (actorNavNoEmail.isBlank()) throw InvalidScoringRequestException("An administrator identity is required")
        val adjustment = repository.addAdjustment(
            participantId,
            pointsDelta,
            reason.trim(),
            actorNavNoEmail,
            sourceCreditId,
        )
        auditService?.record(
            action = "POINTS_ADJUSTED",
            outcome = AuditOutcome.SUCCEEDED,
            actorNavNoEmail = actorNavNoEmail,
            targetParticipantId = participantId,
            details = mapOf(
                "pointsDelta" to pointsDelta,
                "reason" to reason.trim(),
            ),
        )
        return adjustment
    }

    fun updateNextResetDate(newDate: LocalDate, actorNavNoEmail: String): SeasonSummary {
        val today = LocalDate.now(PROGRAM_TIME_ZONE)
        val season = repository.currentSeason()
        if (!newDate.isAfter(today) || !newDate.isAfter(season.startsOn)) {
            throw InvalidScoringRequestException("The next reset date must be after today")
        }
        val updatedSeason = repository.updateNextResetDate(newDate, actorNavNoEmail)
        auditService?.record(
            action = "SEASON_RESET_DATE_UPDATED",
            outcome = AuditOutcome.SUCCEEDED,
            actorNavNoEmail = actorNavNoEmail,
            details = mapOf("nextResetDate" to newDate.toString()),
        )
        return updatedSeason
    }

    fun resetManually(confirmed: Boolean, reason: String, actorNavNoEmail: String): SeasonSummary {
        if (!confirmed) throw InvalidScoringRequestException("Confirmation is required")
        if (reason.isBlank()) throw InvalidScoringRequestException("A reason is required")
        val today = LocalDate.now(PROGRAM_TIME_ZONE)
        if (!today.isAfter(repository.currentSeason().startsOn)) {
            throw InvalidScoringRequestException("A season has already started today")
        }
        val newSeason = repository.resetManually(today, reason.trim(), actorNavNoEmail)
        auditService?.record(
            action = "SEASON_RESET_MANUAL",
            outcome = AuditOutcome.SUCCEEDED,
            actorNavNoEmail = actorNavNoEmail,
            details = mapOf("seasonId" to newSeason.id.toString(), "startsOn" to newSeason.startsOn.toString()),
        )
        return newSeason
    }

    fun resetIfDue(): Boolean {
        val reset = repository.resetIfDue(LocalDate.now(PROGRAM_TIME_ZONE))
        if (reset) {
            auditService?.record(
                action = "SEASON_RESET_SCHEDULED",
                outcome = AuditOutcome.SUCCEEDED,
            )
        }
        return reset
    }

    private fun ranked(scores: List<ParticipantSeasonScore>): List<RankedScore> {
        val sorted = scores.sortedWith(
            compareByDescending<ParticipantSeasonScore> { it.points }
                .thenBy { it.fullName.lowercase() }
                .thenBy { it.participantId },
        )
        var currentRank = 0
        var previousPoints: Long? = null
        return sorted.mapIndexed { index, score ->
            if (score.points != previousPoints) currentRank = index + 1
            previousPoints = score.points
            RankedScore(score.participantId, score.fullName, score.points, currentRank)
        }
    }

    private fun levelFor(points: Long): String =
        when {
            points >= 500 -> "Expert"
            points >= 250 -> "Adept"
            points >= 100 -> "Apprentice"
            else -> "Novice"
        }

    private data class RankedScore(
        val participantId: UUID,
        val fullName: String,
        val points: Long,
        val rank: Int,
    )
}
