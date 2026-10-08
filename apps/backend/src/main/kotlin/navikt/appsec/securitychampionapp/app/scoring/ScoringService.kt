package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private val PROGRAM_TIME_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

@Service
class ScoringService(
    private val repository: ScoringLedger,
    private val auditService: ProgramAuditService? = null,
) {
    @Transactional
    fun adminOverview(): AdminScoringOverview {
        val configuration = repository.configuration()
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
                    level = configuration.levelFor(it.points),
                )
            },
            configuration = configuration,
        )
    }

    fun recognition(): List<RecognitionEntry> =
        ranked(repository.scoresForCurrentSeason(activeOnly = true))
            .filter { it.points > 0 && it.rank <= 5 }
            .map { RecognitionEntry(it.fullName, it.rank) }

    @Transactional
    fun leaderboard(currentParticipantId: UUID? = null): List<LeaderboardEntry> {
        val configuration = repository.configuration()
        return ranked(repository.scoresForCurrentSeason(activeOnly = true))
            .filter { it.points > 0 }
            .map {
                LeaderboardEntry(
                    it.fullName,
                    it.rank,
                    it.points,
                    configuration.levelFor(it.points),
                    isCurrentUser = it.participantId == currentParticipantId,
                )
            }
    }

    @Transactional
    fun ownScore(participantId: UUID): OwnSeasonScore {
        val configuration = repository.configuration()
        val season = repository.currentSeason()
        val points = repository.scoreForParticipant(participantId, season.id)
        val rank = if (points > 0) {
            ranked(repository.scoresForSeason(season.id, activeOnly = true))
                .firstOrNull { it.participantId == participantId }
                ?.rank
        } else {
            null
        }
        return OwnSeasonScore(season, points, configuration.levelFor(points), rank, configuration.tiers)
    }

    fun creditsForParticipant(participantId: UUID): List<ActivityCredit> {
        if (!repository.participantExists(participantId)) throw ScoringTargetNotFoundException()
        return repository.creditsForParticipant(participantId)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun scoringHistoryForParticipant(participantId: UUID): ParticipantScoringHistory {
        if (!repository.participantExists(participantId)) throw ScoringTargetNotFoundException()
        val currentSeason = repository.currentSeason()
        val entries = repository.scoringHistoryForParticipant(participantId)
        val bySeason = entries.groupBy { it.seasonId }
        val seasonIds = bySeason.keys + currentSeason.id
        val seasons = seasonIds.map { seasonId ->
            val history = bySeason[seasonId].orEmpty()
            val first = history.firstOrNull()
            ParticipantScoringSeason(
                id = seasonId,
                startsOn = first?.seasonStartsOn ?: currentSeason.startsOn,
                endsOn = first?.seasonEndsOn,
                points = history.sumOf { it.points.toLong() },
                creditPoints = ActivityCreditType.entries.associateWith { creditType ->
                    history.filter { it.type == ScoringHistoryEntryType.CREDIT && it.creditType == creditType }
                        .sumOf { it.points.toLong() }
                },
                adjustmentPoints = history.filter { it.type == ScoringHistoryEntryType.ADJUSTMENT }
                    .sumOf { it.points.toLong() },
                scoringRulePoints = history.filter { it.type == ScoringHistoryEntryType.SCORING_RULE_CHANGE }
                    .sumOf { it.points.toLong() },
            )
        }.sortedByDescending { it.startsOn }
        return ParticipantScoringHistory(currentSeason.id, seasons, entries)
    }

    @Transactional
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
        recordAward(participantId, creditType, uniquenessKey, sourceReference, auditCorrelationId, result)
        return result
    }

    @Transactional
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
        recordAward(participantId, creditType, uniquenessKey, sourceReference, auditCorrelationId, result)
        return result
    }

    private fun recordAward(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID?,
        result: CreditAwardResult,
    ) {
        if (result == CreditAwardResult.AWARDED && auditService != null) {
            auditService.record(
                action = "CREDIT_AWARDED",
                outcome = AuditOutcome.SUCCEEDED,
                targetParticipantId = participantId,
                correlationId = auditCorrelationId,
                details = mapOf(
                    "creditType" to creditType.name,
                    "points" to repository.creditPoints(participantId, creditType, uniquenessKey),
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

    private data class RankedScore(
        val participantId: UUID,
        val fullName: String,
        val points: Long,
        val rank: Int,
    )
}
