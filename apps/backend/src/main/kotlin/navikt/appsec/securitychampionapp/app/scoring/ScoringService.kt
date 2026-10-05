package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.postgress.ScoringRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val PROGRAM_TIME_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

@Service
class ScoringService(
    private val repository: ScoringRepository,
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
        return OwnSeasonScore(season, points, levelFor(points))
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
    ): CreditAwardResult {
        if (uniquenessKey.isBlank() || sourceReference.isBlank()) {
            throw InvalidScoringRequestException("A source reference and uniqueness key are required")
        }
        return repository.awardCredit(participantId, creditType, uniquenessKey, sourceReference)
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
        return repository.addAdjustment(
            participantId,
            pointsDelta,
            reason.trim(),
            actorNavNoEmail,
            sourceCreditId,
        )
    }

    fun updateNextResetDate(newDate: LocalDate, actorNavNoEmail: String): SeasonSummary {
        val today = LocalDate.now(PROGRAM_TIME_ZONE)
        val season = repository.currentSeason()
        if (!newDate.isAfter(today) || !newDate.isAfter(season.startsOn)) {
            throw InvalidScoringRequestException("The next reset date must be after today")
        }
        return repository.updateNextResetDate(newDate, actorNavNoEmail)
    }

    fun resetManually(confirmed: Boolean, reason: String, actorNavNoEmail: String): SeasonSummary {
        if (!confirmed) throw InvalidScoringRequestException("Confirmation is required")
        if (reason.isBlank()) throw InvalidScoringRequestException("A reason is required")
        val today = LocalDate.now(PROGRAM_TIME_ZONE)
        if (!today.isAfter(repository.currentSeason().startsOn)) {
            throw InvalidScoringRequestException("A season has already started today")
        }
        return repository.resetManually(today, reason.trim(), actorNavNoEmail)
    }

    fun resetIfDue(): Boolean = repository.resetIfDue(LocalDate.now(PROGRAM_TIME_ZONE))

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
            RankedScore(score.fullName, score.points, currentRank)
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
        val fullName: String,
        val points: Long,
        val rank: Int,
    )
}
