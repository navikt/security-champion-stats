package navikt.appsec.securitychampionapp.app.scoring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

class ScoringServiceTest {
    private val repository: ScoringLedger = mock()
    private val service = ScoringService(repository)
    private val season = SeasonSummary(
        id = UUID.randomUUID(),
        startsOn = LocalDate.parse("2026-01-01"),
        endsOn = null,
        nextResetDate = LocalDate.parse("2027-01-01"),
    )

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
