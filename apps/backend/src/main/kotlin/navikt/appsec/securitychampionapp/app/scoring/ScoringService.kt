package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun scoreSummaryForParticipant(
        participantId: UUID,
        selectedSeason: String?,
        admin: Boolean,
    ): Any {
        if (!repository.participantExists(participantId)) throw ScoringTargetNotFoundException()
        val currentSeason = repository.currentSeason()
        val availableSeasons = repository.scoreHistorySeasons()
        val season = when {
            selectedSeason == null -> currentSeason.id
            selectedSeason == "all" -> null
            else -> {
                val seasonId = try {
                    UUID.fromString(selectedSeason)
                } catch (_: IllegalArgumentException) {
                    throw InvalidScoringRequestException("The season ID is invalid")
                }
                if (availableSeasons.none { it.id == seasonId }) {
                    throw InvalidScoringRequestException("The season does not exist")
                }
                seasonId
            }
        }

        val records = repository.scoringHistoryForParticipant(participantId)
        val selectedRecords = records.filter { season == null || it.seasonId == season }
        val points = selectedRecords.sumOf { it.points.toLong() }
        val rank = season?.takeIf { points > 0 }?.let { seasonId ->
            ranked(repository.scoresForSeason(seasonId, activeOnly = true))
                .firstOrNull { it.participantId == participantId }
                ?.rank
        }
        val ruleChanges = selectedRecords
            .filter { it.type == ScoringHistoryEntryType.SCORING_RULE_CHANGE }
            .sumOf { it.points.toLong() }
        val manualAdjustments = selectedRecords
            .filter { it.type == ScoringHistoryEntryType.ADJUSTMENT }
            .sumOf { it.points.toLong() }

        val summary = ScoreSummary(
            points = points,
            tier = repository.configuration().levelFor(points),
            rank = rank,
            breakdown = ScoreBreakdown(
                slack = selectedRecords.pointsFor(ActivityCreditType.SLACK_WEEK),
                deltaRegistration = selectedRecords.pointsFor(ActivityCreditType.DELTA_REGISTRATION),
                githubCommit = selectedRecords.pointsFor(ActivityCreditType.GITHUB_COMMIT),
                githubPullRequest = selectedRecords.pointsFor(ActivityCreditType.GITHUB_PULL_REQUEST),
                securityEvent = selectedRecords.pointsFor(ActivityCreditType.SECURITY_EVENT_CONTRIBUTION),
                adjustments = manualAdjustments + if (admin) 0 else ruleChanges,
                ruleChanges = if (admin) ruleChanges else 0,
            ),
            seasons = availableSeasons,
        )
        return if (admin) {
            summary
        } else {
            ParticipantScoreSummary(
                points = summary.points,
                tier = summary.tier,
                rank = summary.rank,
                breakdown = ParticipantScoreBreakdown(
                    slack = summary.breakdown.slack,
                    deltaRegistration = summary.breakdown.deltaRegistration,
                    githubCommit = summary.breakdown.githubCommit,
                    githubPullRequest = summary.breakdown.githubPullRequest,
                    securityEvent = summary.breakdown.securityEvent,
                    adjustments = summary.breakdown.adjustments,
                ),
                seasons = summary.seasons,
            )
        }
    }

    @Transactional(readOnly = true)
    fun participantScoreHistoryPage(
        participantId: UUID,
        season: String?,
        type: String?,
        cursor: String?,
        limit: Int?,
        admin: Boolean,
    ): ScoreHistoryPage<*> {
        if (!repository.participantExists(participantId)) throw ScoringTargetNotFoundException()
        val pageSize = limit ?: 25
        if (pageSize !in 1..25) throw InvalidScoringRequestException("The page size must be between 1 and 25")
        val normalizedType = type ?: "all"
        if (normalizedType !in setOf("all", "credit", "adjustment", "membership")) {
            throw InvalidScoringRequestException("The history type is invalid")
        }
        val seasonId = when {
            season == null || season == "all" -> null
            else -> try {
                UUID.fromString(season)
            } catch (_: IllegalArgumentException) {
                throw InvalidScoringRequestException("The season ID is invalid")
            }
        }
        if (seasonId != null && repository.scoreHistorySeasons().none { it.id == seasonId }) {
            throw InvalidScoringRequestException("The season does not exist")
        }
        val decodedCursor = cursor?.let(::decodeScoreHistoryCursor)
        val records = repository.scoreHistoryPage(
            participantId = participantId,
            seasonId = seasonId,
            type = normalizedType,
            cursor = decodedCursor,
            limit = pageSize + 1,
        )
        val hasMore = records.size > pageSize
        val pageRecords = records.take(pageSize)
        val nextCursor = if (hasMore) encodeScoreHistoryCursor(pageRecords.last()) else null

        return if (admin) {
            ScoreHistoryPage(
                entries = pageRecords.map { record ->
                    AdminScoreHistoryEntry(
                        id = record.id,
                        kind = record.type.toHistoryKind(),
                        recordedAt = record.recordedAt,
                        activityAt = record.activityAt,
                        creditType = record.creditType,
                        points = record.points,
                        displayName = record.displayName,
                        sourceRef = record.sourceReference,
                        creditId = record.creditId,
                        seasonId = record.seasonId,
                        reason = record.reason,
                        adminName = record.adminName,
                        linkedCreditId = record.linkedCreditId,
                        revokedAt = record.revokedAt,
                        action = record.membershipAction,
                        membershipStatusBefore = record.membershipStatusBefore,
                        membershipStatusAfter = record.membershipStatusAfter,
                        membershipReason = record.membershipReason,
                        ruleChange = record.type == "SCORING_RULE_CHANGE",
                    )
                },
                nextCursor = nextCursor,
            )
        } else {
            ScoreHistoryPage(
                entries = pageRecords.map { record ->
                    ParticipantScoreHistoryEntry(
                        kind = record.type.toHistoryKind(),
                        occurredAt = if (record.type == "CREDIT") {
                            record.activityAt ?: record.recordedAt
                        } else {
                            record.recordedAt
                        },
                        creditType = record.creditType,
                        points = record.points,
                        displayName = record.displayName,
                        action = record.membershipAction,
                        membershipStatusBefore = record.membershipStatusBefore,
                        membershipStatusAfter = record.membershipStatusAfter,
                    )
                },
                nextCursor = nextCursor,
            )
        }
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

    private fun List<ScoringHistoryEntry>.pointsFor(type: ActivityCreditType): Long =
        filter { it.type == ScoringHistoryEntryType.CREDIT && it.creditType == type }
            .sumOf { it.points.toLong() }

    private fun String.toHistoryKind(): String = when (this) {
        "CREDIT" -> "credit"
        "MEMBERSHIP" -> "membership"
        else -> "adjustment"
    }

    private fun encodeScoreHistoryCursor(record: ScoreHistoryRecord): String {
        val value = "${record.recordedAt}|${record.tieIndex}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
    }

    private fun decodeScoreHistoryCursor(value: String): ScoreHistoryCursor {
        val decoded = try {
            String(Base64.getUrlDecoder().decode(value))
        } catch (_: IllegalArgumentException) {
            throw InvalidScoringRequestException("The history cursor is invalid")
        }
        val parts = decoded.split("|", limit = 2)
        if (parts.size != 2) throw InvalidScoringRequestException("The history cursor is invalid")
        val timestamp = try {
            Instant.parse(parts[0])
        } catch (_: java.time.format.DateTimeParseException) {
            throw InvalidScoringRequestException("The history cursor is invalid")
        }
        val tieIndex = parts[1].toLongOrNull()
            ?: throw InvalidScoringRequestException("The history cursor is invalid")
        if (tieIndex < 1) throw InvalidScoringRequestException("The history cursor is invalid")
        return ScoreHistoryCursor(timestamp, tieIndex)
    }

    private data class RankedScore(
        val participantId: UUID,
        val fullName: String,
        val points: Long,
        val rank: Int,
    )
}
