package navikt.appsec.securitychampionapp.integrations.postgress

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class AdminDashboardMetrics(
    val activeParticipantCount: Int,
    val eventRegistrationCount: Long,
    val pointsByCreditType: Map<String, Long>,
    val weeklyPoints: List<WeeklyCreditPoints>,
)

data class WeeklyCreditPoints(
    val weekStarting: LocalDate,
    val creditType: String,
    val points: Long,
)

@Repository
class AdminDashboardRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val osloZone = ZoneId.of("Europe/Oslo")

    fun metrics(
        seasonId: UUID,
        seasonStartsOn: LocalDate,
        throughDate: LocalDate,
    ): AdminDashboardMetrics {
        val endExclusive = Timestamp.from(throughDate.plusDays(1).atStartOfDay(osloZone).toInstant())
        val activeParticipantCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM program_participants WHERE status = 'ACTIVE'",
            Int::class.javaObjectType,
        ) ?: 0
        val totals = jdbcTemplate.query(
            """
                SELECT credit_type, SUM(points)::bigint AS points
                FROM activity_credits
                WHERE season_id = ? AND awarded_at < ?
                GROUP BY credit_type
                UNION ALL
                SELECT 'POINT_ADJUSTMENT' AS credit_type, SUM(points_delta)::bigint AS points
                FROM point_adjustments
                WHERE season_id = ? AND created_at < ?
                HAVING COUNT(*) > 0
            """.trimIndent(),
            RowMapper { rs, _ -> rs.getString("credit_type") to rs.getLong("points") },
            seasonId,
            endExclusive,
            seasonId,
            endExclusive,
        ).toMap()
        val eventRegistrationCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM activity_credits
                WHERE season_id = ? AND credit_type = 'DELTA_REGISTRATION' AND awarded_at < ?
            """.trimIndent(),
            Long::class.javaObjectType,
            seasonId,
            endExclusive,
        ) ?: 0L
        val weeklyPoints = jdbcTemplate.query(
            """
                SELECT week_start, credit_type, SUM(points)::bigint AS points
                FROM (
                    SELECT
                        date_trunc('week', awarded_at AT TIME ZONE 'Europe/Oslo')::date AS week_start,
                        credit_type,
                        points
                    FROM activity_credits
                    WHERE season_id = ? AND awarded_at < ?
                    UNION ALL
                    SELECT
                        date_trunc('week', created_at AT TIME ZONE 'Europe/Oslo')::date AS week_start,
                        'POINT_ADJUSTMENT' AS credit_type,
                        points_delta AS points
                    FROM point_adjustments
                    WHERE season_id = ? AND created_at < ?
                ) AS contributions
                GROUP BY week_start, credit_type
                ORDER BY week_start, credit_type
            """.trimIndent(),
            RowMapper { rs, _ ->
                WeeklyCreditPoints(
                    weekStarting = rs.getObject("week_start", LocalDate::class.java),
                    creditType = rs.getString("credit_type"),
                    points = rs.getLong("points"),
                )
            },
            seasonId,
            endExclusive,
            seasonId,
            endExclusive,
        )
        return AdminDashboardMetrics(
            activeParticipantCount = activeParticipantCount,
            eventRegistrationCount = eventRegistrationCount,
            pointsByCreditType = totals,
            weeklyPoints = weeklyPoints.filter {
                !it.weekStarting.isBefore(seasonStartsOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
            },
        )
    }
}
