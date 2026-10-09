package navikt.appsec.securitychampionapp.app.scoring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.any
import java.time.LocalDate
import java.time.Instant
import org.springframework.dao.DataAccessResourceFailureException
import java.util.UUID

class ScoringServiceTest {
    private val repository: ScoringLedger = mock()
    private val service = ScoringService(repository)
    @BeforeEach
    fun configureScoring() {
        whenever(repository.configuration()).thenReturn(defaultScoringConfiguration)
    }
    private val season = SeasonSummary(
        id = UUID.randomUUID(),
        startsOn = LocalDate.parse("2026-01-01"),
        endsOn = null,
        nextResetDate = LocalDate.parse("2027-01-01"),
    )

    @Test
    fun `should total history by assigned season rather than the correction date`() {
        val participant = UUID.randomUUID()
        val previous = season.copy(id = UUID.randomUUID(), startsOn = LocalDate.of(2025, 1, 1), endsOn = LocalDate.of(2025, 12, 31))
        val recordedAt = Instant.parse("2026-10-01T12:00:00Z")
        val credit = ScoringHistoryEntry(
            UUID.randomUUID(), ScoringHistoryEntryType.CREDIT, recordedAt, null,
            previous.id, previous.startsOn, previous.endsOn, 3, ActivityCreditType.GITHUB_PULL_REQUEST,
            "pull:1", null, null, null, null,
        )
        val manual = credit.copy(id = UUID.randomUUID(), type = ScoringHistoryEntryType.ADJUSTMENT, points = -1, sourceCreditId = credit.id)
        val rule = manual.copy(id = UUID.randomUUID(), type = ScoringHistoryEntryType.SCORING_RULE_CHANGE, points = 2)
        val zeroCredit = credit.copy(id = UUID.randomUUID(), seasonId = season.id, seasonStartsOn = season.startsOn, seasonEndsOn = null, points = 0)
        whenever(repository.participantExists(participant)).thenReturn(true)
        whenever(repository.currentSeason()).thenReturn(season)
        whenever(repository.scoringHistoryForParticipant(participant)).thenReturn(listOf(rule, manual, zeroCredit, credit))

        val result = service.scoringHistoryForParticipant(participant)

        assertEquals(season.id, result.currentSeasonId)
        assertEquals(listOf(season.id, previous.id), result.seasons.map { it.id })
        assertEquals(listOf(0L, 4L), result.seasons.map { it.points })
        assertEquals(3L, result.seasons[1].creditPoints[ActivityCreditType.GITHUB_PULL_REQUEST])
        assertEquals(-1L, result.seasons[1].adjustmentPoints)
        assertEquals(2L, result.seasons[1].scoringRulePoints)
        assertEquals(listOf(rule, manual, zeroCredit, credit), result.entries)
    }

    @Test
    fun `should keep the participant summary privacy shape and fold rule changes into adjustments`() {
        val participant = UUID.randomUUID()
        val previous = season.copy(
            id = UUID.randomUUID(),
            startsOn = LocalDate.of(2025, 1, 1),
            endsOn = LocalDate.of(2025, 12, 31),
        )
        val recordedAt = Instant.parse("2026-10-01T12:00:00Z")
        val records = listOf(
            ScoringHistoryEntry(
                UUID.randomUUID(), ScoringHistoryEntryType.CREDIT, recordedAt, null,
                season.id, season.startsOn, null, 4, ActivityCreditType.SLACK_WEEK,
                "source:1", null, null, null, null,
            ),
            ScoringHistoryEntry(
                UUID.randomUUID(), ScoringHistoryEntryType.ADJUSTMENT, recordedAt, null,
                season.id, season.startsOn, null, -1, null,
                null, null, "correction", "admin@nav.no", null,
            ),
            ScoringHistoryEntry(
                UUID.randomUUID(), ScoringHistoryEntryType.SCORING_RULE_CHANGE, recordedAt, null,
                season.id, season.startsOn, null, 2, null,
                null, null, "rule update", "admin@nav.no", null,
            ),
            ScoringHistoryEntry(
                UUID.randomUUID(), ScoringHistoryEntryType.CREDIT, recordedAt, null,
                previous.id, previous.startsOn, previous.endsOn, 3, ActivityCreditType.GITHUB_COMMIT,
                "source:2", null, null, null, null,
            ),
        )
        whenever(repository.participantExists(participant)).thenReturn(true)
        whenever(repository.currentSeason()).thenReturn(season)
        whenever(repository.scoreHistorySeasons()).thenReturn(
            listOf(
                ScoreHistorySeason(season.id, season.startsOn, season.endsOn),
                ScoreHistorySeason(previous.id, previous.startsOn, previous.endsOn),
            ),
        )
        whenever(repository.scoringHistoryForParticipant(participant)).thenReturn(records)
        whenever(repository.scoreForParticipant(participant, season.id)).thenReturn(5L)
        whenever(repository.scoresForSeason(season.id, activeOnly = true)).thenReturn(
            listOf(score(participant, "Person", 5L), score(UUID.randomUUID(), "Another", 10L)),
        )

        val personal = service.scoreSummaryForParticipant(participant, null, admin = false) as ParticipantScoreSummary
        val admin = service.scoreSummaryForParticipant(participant, null, admin = true) as ScoreSummary

        assertEquals(5L, personal.points)
        assertEquals("Novice", personal.tier)
        assertEquals(2, personal.rank)
        assertEquals(4L, personal.breakdown.slack)
        assertEquals(1L, personal.breakdown.adjustments)
        assertEquals(8L, service.scoreSummaryForParticipant(participant, "all", admin = false)
            .let { (it as ParticipantScoreSummary).points })
        assertEquals(null, service.scoreSummaryForParticipant(participant, "all", admin = false)
            .let { (it as ParticipantScoreSummary).rank })
        assertEquals(-1L, admin.breakdown.adjustments)
        assertEquals(2L, admin.breakdown.ruleChanges)
    }

    @Test
    fun `should calculate tier and rank from the selected history season`() {
        val participant = UUID.randomUUID()
        val previous = season.copy(
            id = UUID.randomUUID(),
            startsOn = LocalDate.of(2025, 1, 1),
            endsOn = LocalDate.of(2025, 12, 31),
        )
        whenever(repository.configuration()).thenReturn(
            defaultScoringConfiguration.copy(
                tiers = listOf(ScoringTier("Starter", 0), ScoringTier("Champion", 5)),
            ),
        )
        whenever(repository.participantExists(participant)).thenReturn(true)
        whenever(repository.currentSeason()).thenReturn(season)
        whenever(repository.scoreForParticipant(participant, season.id)).thenReturn(10L)
        whenever(repository.scoreHistorySeasons()).thenReturn(
            listOf(
                ScoreHistorySeason(season.id, season.startsOn, season.endsOn),
                ScoreHistorySeason(previous.id, previous.startsOn, previous.endsOn),
            ),
        )
        whenever(repository.scoresForSeason(previous.id, activeOnly = true)).thenReturn(
            listOf(score(participant, "Person", 0L)),
        )

        listOf(0, -1).forEach { points ->
            val selectedRecord = ScoringHistoryEntry(
                UUID.randomUUID(),
                ScoringHistoryEntryType.ADJUSTMENT,
                Instant.parse("2026-10-01T12:00:00Z"),
                null,
                previous.id,
                previous.startsOn,
                previous.endsOn,
                points,
                null,
                null,
                null,
                "historical correction",
                "admin@nav.no",
                null,
            )
            whenever(repository.scoringHistoryForParticipant(participant)).thenReturn(
                listOf(
                    selectedRecord,
                    selectedRecord.copy(
                        id = UUID.randomUUID(),
                        seasonId = season.id,
                        seasonStartsOn = season.startsOn,
                        seasonEndsOn = null,
                        points = 10,
                    ),
                ),
            )

            val summary = service.scoreSummaryForParticipant(
                participant,
                previous.id.toString(),
                admin = false,
            ) as ParticipantScoreSummary

            assertEquals(points.toLong(), summary.points)
            assertEquals("Starter", summary.tier)
            assertEquals(null, summary.rank)
        }
    }

    @Test
    fun `should return an empty current season without inventing history entries`() {
        val participant = UUID.randomUUID()
        whenever(repository.participantExists(participant)).thenReturn(true)
        whenever(repository.currentSeason()).thenReturn(season)
        whenever(repository.scoringHistoryForParticipant(participant)).thenReturn(emptyList())

        val result = service.scoringHistoryForParticipant(participant)
        assertEquals(emptyList<ScoringHistoryEntry>(), result.entries)
        assertEquals(season.id, result.seasons.single().id)
        assertEquals(0L, result.seasons.single().points)
        assertEquals(ActivityCreditType.entries.associateWith { 0L }, result.seasons.single().creditPoints)
    }

    @Test
    fun `should not hide history lookup failures or read a missing participant ledger`() {
        val participant = UUID.randomUUID()
        assertThrows(ScoringTargetNotFoundException::class.java) { service.scoringHistoryForParticipant(participant) }
        verify(repository, never()).scoringHistoryForParticipant(any())

        whenever(repository.participantExists(participant)).thenReturn(true)
        whenever(repository.currentSeason()).thenReturn(season)
        whenever(repository.scoringHistoryForParticipant(participant)).thenThrow(DataAccessResourceFailureException("Unavailable"))
        assertThrows(DataAccessResourceFailureException::class.java) { service.scoringHistoryForParticipant(participant) }
    }

    @Test
    fun `should use saved custom tier names and thresholds for negative and boundary scores`() {
        val participant = UUID.randomUUID()
        whenever(repository.configuration()).thenReturn(defaultScoringConfiguration.copy(
            tiers = listOf(ScoringTier("Starter", 0), ScoringTier("Champion", 5)),
        ))
        whenever(repository.currentSeason()).thenReturn(season)
        listOf(-2L to "Starter", 0L to "Starter", 4L to "Starter", 5L to "Champion").forEach { (points, level) ->
            whenever(repository.scoreForParticipant(participant, season.id)).thenReturn(points)
            whenever(repository.scoresForSeason(season.id, activeOnly = true)).thenReturn(emptyList())
            assertEquals(level, service.ownScore(participant).level)
        }
    }

    @Test
    fun `should return participant rank using shared competition ranks`() {
        val first = UUID.randomUUID()
        val tied = UUID.randomUUID()
        val participant = UUID.randomUUID()
        whenever(repository.currentSeason()).thenReturn(season)
        whenever(repository.scoreForParticipant(participant, season.id)).thenReturn(5L)
        whenever(repository.scoresForSeason(season.id, activeOnly = true)).thenReturn(
            listOf(
                score(first, "A", 10L),
                score(tied, "B", 10L),
                score(participant, "C", 5L),
            ),
        )

        val result = service.ownScore(participant)

        assertEquals(5L, result.points)
        assertEquals(3, result.rank)
    }

    @Test
    fun `should omit rank for a participant with no points`() {
        val participant = UUID.randomUUID()
        whenever(repository.currentSeason()).thenReturn(season)
        whenever(repository.scoreForParticipant(participant, season.id)).thenReturn(0L)

        val result = service.ownScore(participant)

        assertEquals(0L, result.points)
        assertEquals(null, result.rank)
    }

    @Test
    fun `should identify the current participant in leaderboard without exposing their id`() {
        val currentParticipantId = UUID.randomUUID()
        val otherParticipantId = UUID.randomUUID()
        whenever(repository.scoresForCurrentSeason(activeOnly = true)).thenReturn(
            listOf(
                score(currentParticipantId, "Same Name", 10L),
                score(otherParticipantId, "Same Name", 5L),
            ),
        )

        val result = service.leaderboard(currentParticipantId)

        assertEquals(listOf(true, false), result.map { it.isCurrentUser })
        assertEquals("Same Name", result[0].fullName)
    }

    @Test
    fun `should assign levels at the approved current season thresholds`() {
        val participant = UUID.randomUUID()
        whenever(repository.currentSeason()).thenReturn(season)
        val expectedLevels = listOf(
            0L to "Novice",
            99L to "Novice",
            100L to "Apprentice",
            249L to "Apprentice",
            250L to "Adept",
            499L to "Adept",
            500L to "Expert",
            750L to "Expert",
        )

        expectedLevels.forEach { (points, expectedLevel) ->
            whenever(repository.scoreForParticipant(participant, season.id)).thenReturn(points)
            whenever(repository.scoresForSeason(season.id, activeOnly = true)).thenReturn(
                listOf(score(participant, "Person", points)),
            )

            assertEquals(expectedLevel, service.ownScore(participant).level)
        }
    }

    private fun score(id: UUID, name: String, points: Long) =
        ParticipantSeasonScore(id, name, "$name@nav.no", true, points)
}
