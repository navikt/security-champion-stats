package navikt.appsec.securitychampionapp.app.dashboard

import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncStatusView
import navikt.appsec.securitychampionapp.app.scoring.GitHubScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.GitHubSyncStatusView
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.SlackScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.SlackSyncStatusView
import navikt.appsec.securitychampionapp.integrations.postgress.AdminDashboardRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private val ADMIN_DASHBOARD_ZONE = ZoneId.of("Europe/Oslo")

data class CreditTypeTotal(
    val creditType: String,
    val points: Long,
)

data class WeeklyCreditTotals(
    val weekStarting: LocalDate,
    val pointsByCreditType: Map<String, Long>,
)

data class AdminDashboardOverview(
    val season: SeasonSummary,
    val today: LocalDate,
    val activeParticipantCount: Int,
    val eventRegistrationCount: Long,
    val pointsByCreditType: List<CreditTypeTotal>,
    val weeklyTotals: List<WeeklyCreditTotals>,
    val slack: SlackSyncStatusView,
    val delta: DeltaSyncStatusView,
    val github: GitHubSyncStatusView,
)

@Service
class AdminDashboardService(
    private val scoringRepository: ScoringLedger,
    private val dashboardRepository: AdminDashboardRepository,
    private val slackStatusService: SlackScoringStatusService,
    private val deltaStatusService: DeltaScoringStatusService,
    private val clock: Clock,
    private val gitHubStatusService: GitHubScoringStatusService,
) {
    fun overview(): AdminDashboardOverview {
        val season = scoringRepository.currentSeason()
        val today = LocalDate.now(clock.withZone(ADMIN_DASHBOARD_ZONE))
        val metrics = dashboardRepository.metrics(season.id, season.startsOn, today)
        val creditTypes = ActivityCreditType.entries.map { it.name } + POINT_ADJUSTMENT
        val weeklyByDate = metrics.weeklyPoints.groupBy { it.weekStarting }
        val firstWeek = season.startsOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeklyTotals = generateSequence(firstWeek) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(lastWeek) }
            .map { week ->
                val totals = weeklyByDate[week].orEmpty().associate { it.creditType to it.points }
                WeeklyCreditTotals(
                    weekStarting = week,
                    pointsByCreditType = creditTypes.associateWith { totals[it] ?: 0L },
                )
            }
            .toList()

        return AdminDashboardOverview(
            season = season,
            today = today,
            activeParticipantCount = metrics.activeParticipantCount,
            eventRegistrationCount = metrics.eventRegistrationCount,
            pointsByCreditType = creditTypes.map {
                CreditTypeTotal(it, metrics.pointsByCreditType[it] ?: 0L)
            },
            weeklyTotals = weeklyTotals,
            slack = slackStatusService.status(),
            delta = deltaStatusService.status(),
            github = gitHubStatusService.status(),
        )
    }

    private companion object {
        const val POINT_ADJUSTMENT = "POINT_ADJUSTMENT"
    }
}
