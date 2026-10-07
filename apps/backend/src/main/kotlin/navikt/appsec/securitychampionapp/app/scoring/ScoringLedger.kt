package navikt.appsec.securitychampionapp.app.scoring

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface ScoringLedger {
    fun configuration(): ScoringConfiguration

    fun previewConfiguration(request: ScoringConfigurationRequest): ScoringConfigurationPreview

    fun saveConfiguration(request: ScoringConfigurationRequest, actor: String): ScoringConfiguration

    fun creditPoints(participantId: UUID, creditType: ActivityCreditType, uniquenessKey: String): Int

    fun currentSeason(): SeasonSummary

    fun scoresForCurrentSeason(activeOnly: Boolean = false): List<ParticipantSeasonScore>

    fun scoresForSeason(
        seasonId: UUID,
        activeOnly: Boolean = false,
    ): List<ParticipantSeasonScore>

    fun scoreForParticipant(participantId: UUID, seasonId: UUID): Long

    fun creditsForParticipant(participantId: UUID): List<ActivityCredit>

    fun participantExists(participantId: UUID): Boolean

    fun awardCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID? = null,
    ): CreditAwardResult

    fun awardGitHubCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID?,
        activityAt: Instant,
        expectedSeasonId: UUID,
    ): CreditAwardResult

    fun addAdjustment(
        participantId: UUID,
        pointsDelta: Int,
        reason: String,
        actorNavNoEmail: String,
        sourceCreditId: UUID?,
    ): PointAdjustment

    fun updateNextResetDate(newDate: LocalDate, actorNavNoEmail: String): SeasonSummary

    fun resetManually(startDate: LocalDate, reason: String, actorNavNoEmail: String): SeasonSummary

    fun resetIfDue(today: LocalDate): Boolean
}
