package navikt.appsec.securitychampionapp.app.dashboard

import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncStatusView
import navikt.appsec.securitychampionapp.app.scoring.GitHubScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.GitHubSyncStatusView
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.SlackScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.SlackSyncStatusView
import navikt.appsec.securitychampionapp.integrations.postgress.AdminDashboardMetrics
import navikt.appsec.securitychampionapp.integrations.postgress.AdminDashboardRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ScoringRepository
import navikt.appsec.securitychampionapp.integrations.postgress.WeeklyCreditPoints
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class AdminDashboardServiceTest {
    @Test
    fun `should include every season week and aggregate point types without participant details`() {
        val season = SeasonSummary(
            id = UUID.randomUUID(),
            startsOn = LocalDate.parse("2026-01-01"),
            endsOn = null,
            nextResetDate = LocalDate.parse("2027-01-01"),
        )
        val scoringRepository = mock<ScoringRepository>()
        val dashboardRepository = mock<AdminDashboardRepository>()
        val slackStatusService = mock<SlackScoringStatusService>()
        val deltaStatusService = mock<DeltaScoringStatusService>()
        val gitHubStatusService = mock<GitHubScoringStatusService>()
        whenever(scoringRepository.currentSeason()).thenReturn(season)
        whenever(
            dashboardRepository.metrics(season.id, season.startsOn, LocalDate.parse("2026-01-14")),
        ).thenReturn(
            AdminDashboardMetrics(
                activeParticipantCount = 2,
                eventRegistrationCount = 1,
                pointsByCreditType = mapOf("DELTA_REGISTRATION" to 1L),
                weeklyPoints = listOf(
                    WeeklyCreditPoints(LocalDate.parse("2026-01-05"), "DELTA_REGISTRATION", 1L),
                ),
            ),
        )
        whenever(slackStatusService.status()).thenReturn(emptySlackStatus())
        whenever(deltaStatusService.status()).thenReturn(emptyDeltaStatus())
        whenever(gitHubStatusService.status()).thenReturn(GitHubSyncStatusView(false, null, null, null, 0, 0, 0, 0, null))
        val clock = Clock.fixed(
            Instant.parse("2026-01-14T12:00:00Z"),
            ZoneId.of("Europe/Oslo"),
        )
        val service = AdminDashboardService(
            scoringRepository,
            dashboardRepository,
            slackStatusService,
            deltaStatusService,
            clock,
            gitHubStatusService,
        )

        val overview = service.overview()

        assertThat(overview.activeParticipantCount).isEqualTo(2)
        assertThat(overview.eventRegistrationCount).isEqualTo(1)
        assertThat(overview.pointsByCreditType).contains(
            CreditTypeTotal("DELTA_REGISTRATION", 1),
            CreditTypeTotal("POINT_ADJUSTMENT", 0),
        )
        assertThat(overview.weeklyTotals.map { it.weekStarting }).containsExactly(
            LocalDate.parse("2025-12-29"),
            LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-12"),
        )
        assertThat(overview.weeklyTotals.first().pointsByCreditType["DELTA_REGISTRATION"]).isZero()
        assertThat(overview.weeklyTotals[1].pointsByCreditType["DELTA_REGISTRATION"]).isEqualTo(1)
        assertThat(overview.weeklyTotals.last().pointsByCreditType["DELTA_REGISTRATION"]).isZero()
    }

    private fun emptySlackStatus() = SlackSyncStatusView(
        enabled = true,
        lastAttemptAt = null,
        lastSuccessAt = null,
        outcome = null,
        messagesScanned = 0,
        creditsAwarded = 0,
        duplicateCredits = 0,
        unmappedAuthors = 0,
        failureSummary = null,
    )

    private fun emptyDeltaStatus() = DeltaSyncStatusView(
        enabled = false,
        lastAttemptAt = null,
        lastSuccessAt = null,
        outcome = null,
        eventsScanned = 0,
        creditsAwarded = 0,
        duplicateCredits = 0,
        unmatchedRegistrations = 0,
        failedEvents = 0,
        failureSummary = null,
    )
}
