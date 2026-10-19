package navikt.appsec.securitychampionapp.app.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ScoringConfigurationChangesTest {
    private val season = SeasonSummary(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), LocalDate.of(2026, 1, 1), null, LocalDate.of(2027, 1, 1))
    private val tiers = listOf(ScoringTier("Bronze", 0), ScoringTier("Silver", 10))
    private val before = ScoringConfiguration(3, tiers, ActivityCreditType.entries.map { ActivityPoints(it, 1) })
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun request(
        slackPoints: Int,
        retroactive: Boolean = true,
        newTiers: List<ScoringTier> = tiers,
        reason: String = "Rebalance",
    ) = ScoringConfigurationRequest(
        expectedVersion = 3,
        tiers = newTiers,
        activities = ActivityCreditType.entries.map { ActivityPoints(it, if (it == ActivityCreditType.SLACK_WEEK) slackPoints else 1) },
        applyRetroactively = retroactive,
        reason = reason,
    )

    private fun score(id: UUID, points: Long) = ParticipantSeasonScore(id, "Name $id", "$id@nav.no", true, points)
    private fun credit(id: Int, participant: UUID, type: ActivityCreditType, effective: Long) =
        PricedCredit(UUID(0, id.toLong()), participant, type, effective)

    @Test
    fun `should reprice credits to the desired points and report the level change`() {
        val changes = ScoringConfigurationChanges.calculate(
            request(slackPoints = 6),
            before,
            season,
            listOf(credit(1, first, ActivityCreditType.SLACK_WEEK, 1), credit(2, first, ActivityCreditType.SLACK_WEEK, 1)),
            listOf(score(first, 8)),
        )

        assertThat(changes.credits.map { it.delta }).containsExactly(5, 5)
        assertThat(changes.preview.affectedCredits).isEqualTo(2)
        assertThat(changes.preview.pointsDelta).isEqualTo(10)
        val impact = changes.preview.participants.single()
        assertThat(impact.pointsBefore).isEqualTo(8)
        assertThat(impact.pointsAfter).isEqualTo(18)
        assertThat(impact.levelBefore).isEqualTo("Bronze")
        assertThat(impact.levelAfter).isEqualTo("Silver")
    }

    @Test
    fun `should skip credits that already have the desired value including earlier corrections`() {
        val changes = ScoringConfigurationChanges.calculate(
            request(slackPoints = 6),
            before,
            season,
            listOf(credit(1, first, ActivityCreditType.SLACK_WEEK, 6), credit(2, first, ActivityCreditType.GITHUB_COMMIT, 1)),
            listOf(score(first, 7)),
        )

        assertThat(changes.credits).isEmpty()
        assertThat(changes.preview.participants).isEmpty()
    }

    @Test
    fun `should leave credits untouched when the change is not retroactive but still report tier changes`() {
        val changes = ScoringConfigurationChanges.calculate(
            request(slackPoints = 6, retroactive = false, newTiers = listOf(ScoringTier("Bronze", 0), ScoringTier("Silver", 5))),
            before,
            season,
            listOf(credit(1, first, ActivityCreditType.SLACK_WEEK, 1)),
            listOf(score(first, 7), score(second, 2)),
        )

        assertThat(changes.credits).isEmpty()
        assertThat(changes.preview.pointsDelta).isZero()
        val impact = changes.preview.participants.single()
        assertThat(impact.participantId).isEqualTo(first)
        assertThat(impact.levelBefore).isEqualTo("Bronze")
        assertThat(impact.levelAfter).isEqualTo("Silver")
    }

    @Test
    fun `should support lowering points with negative deltas`() {
        val changes = ScoringConfigurationChanges.calculate(
            request(slackPoints = 0),
            before,
            season,
            listOf(credit(1, first, ActivityCreditType.SLACK_WEEK, 1)),
            listOf(score(first, 1)),
        )

        assertThat(changes.credits.single().delta).isEqualTo(-1)
        assertThat(changes.preview.pointsDelta).isEqualTo(-1)
        assertThat(changes.preview.participants.single().pointsAfter).isZero()
    }

    @Test
    fun `should produce a stable token that changes with any input`() {
        val credits = listOf(credit(1, first, ActivityCreditType.SLACK_WEEK, 1))
        val scores = listOf(score(first, 1))
        fun token(request: ScoringConfigurationRequest = request(6), s: List<ParticipantSeasonScore> = scores) =
            ScoringConfigurationChanges.calculate(request, before, season, credits, s).preview.token

        assertThat(token()).isEqualTo(token()).matches("[0-9a-f]{64}")
        assertThat(token(request(7))).isNotEqualTo(token())
        assertThat(token(request(6, reason = "Other"))).isNotEqualTo(token())
        assertThat(token(s = listOf(score(first, 2)))).isNotEqualTo(token())
    }

    @Test
    fun `should keep the legacy token for credits on both sides of the signed uuid boundary`() {
        val credits = listOf(
            PricedCredit(UUID.fromString("80000000-0000-0000-0000-0000000000c2"), second, ActivityCreditType.SLACK_WEEK, 1),
            PricedCredit(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), first, ActivityCreditType.SLACK_WEEK, 1),
        )

        val changes = ScoringConfigurationChanges.calculate(
            request(6), before, season, credits, listOf(score(first, 1), score(second, 1)),
        )

        assertThat(changes.credits.map { it.id.toString() }).containsExactly(
            "00000000-0000-0000-0000-0000000000c1",
            "80000000-0000-0000-0000-0000000000c2",
        )
        // Computed independently from the pre-extraction serialisation, where PostgreSQL ordered credits by unsigned uuid.
        assertThat(changes.preview.token)
            .isEqualTo("7a79fcf583e8598f574750f80defed2aa922d304e2e5e20b45eca1e4bfdf7b7b")
    }

    @Test
    fun `should not depend on the order of the loaded rows`() {
        val credits = listOf(credit(1, first, ActivityCreditType.SLACK_WEEK, 1), credit(2, second, ActivityCreditType.SLACK_WEEK, 1))
        val scores = listOf(score(first, 1), score(second, 1))

        val forward = ScoringConfigurationChanges.calculate(request(6), before, season, credits, scores)
        val reversed = ScoringConfigurationChanges.calculate(request(6), before, season, credits.reversed(), scores.reversed())

        assertThat(reversed).isEqualTo(forward)
    }
}
