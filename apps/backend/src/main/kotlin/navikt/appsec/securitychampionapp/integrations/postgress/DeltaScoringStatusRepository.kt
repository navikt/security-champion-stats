package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncSummary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.sql.Timestamp

enum class DeltaSyncOutcome {
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILURE,
    FAILED,
}

data class DeltaSyncStatusRecord(
    val lastAttemptAt: Instant,
    val lastSuccessAt: Instant?,
    val outcome: DeltaSyncOutcome,
    val eventsScanned: Int,
    val creditsAwarded: Int,
    val duplicateCredits: Int,
    val unmatchedRegistrations: Int,
    val failedEvents: Int,
    val failureSummary: String?,
)

@Repository
class DeltaScoringStatusRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val rowMapper = RowMapper { rs, _ ->
        DeltaSyncStatusRecord(
            lastAttemptAt = rs.getTimestamp("last_attempt_at").toInstant(),
            lastSuccessAt = rs.getTimestamp("last_success_at")?.toInstant(),
            outcome = DeltaSyncOutcome.valueOf(rs.getString("outcome")),
            eventsScanned = rs.getInt("events_scanned"),
            creditsAwarded = rs.getInt("credits_awarded"),
            duplicateCredits = rs.getInt("duplicate_credits"),
            unmatchedRegistrations = rs.getInt("unmatched_registrations"),
            failedEvents = rs.getInt("failed_events"),
            failureSummary = rs.getString("failure_summary"),
        )
    }

    fun find(): DeltaSyncStatusRecord? =
        jdbcTemplate.query(
            """
                SELECT last_attempt_at, last_success_at, outcome, events_scanned,
                    credits_awarded, duplicate_credits, unmatched_registrations, failed_events,
                    failure_summary
                FROM delta_scoring_sync_status
                WHERE singleton = TRUE
            """.trimIndent(),
            rowMapper,
        ).firstOrNull()

    @Transactional
    fun recordStarted(at: Instant) {
        jdbcTemplate.update(
            """
                INSERT INTO delta_scoring_sync_status (
                    singleton, last_attempt_at, outcome
                ) VALUES (TRUE, ?, 'RUNNING')
                ON CONFLICT (singleton) DO UPDATE SET
                    last_attempt_at = EXCLUDED.last_attempt_at,
                    outcome = 'RUNNING',
                    events_scanned = 0,
                    credits_awarded = 0,
                    duplicate_credits = 0,
                    unmatched_registrations = 0,
                    failed_events = 0,
                    failure_summary = NULL
            """.trimIndent(),
            Timestamp.from(at),
        )
    }

    @Transactional
    fun recordSucceeded(
        at: Instant,
        summary: DeltaSyncSummary,
    ) {
        jdbcTemplate.update(
            """
                UPDATE delta_scoring_sync_status
                SET last_success_at = ?,
                    outcome = 'SUCCEEDED',
                    events_scanned = ?,
                    credits_awarded = ?,
                    duplicate_credits = ?,
                    unmatched_registrations = ?,
                    failed_events = 0,
                    failure_summary = NULL
                WHERE singleton = TRUE
            """.trimIndent(),
            Timestamp.from(at),
            summary.eventsScanned,
            summary.creditsAwarded,
            summary.duplicateCredits,
            summary.unmatchedRegistrations,
        )
    }

    @Transactional
    fun recordPartialFailure(
        at: Instant,
        summary: DeltaSyncSummary,
    ) {
        jdbcTemplate.update(
            """
                UPDATE delta_scoring_sync_status
                SET last_attempt_at = ?,
                    outcome = 'PARTIAL_FAILURE',
                    events_scanned = ?,
                    credits_awarded = ?,
                    duplicate_credits = ?,
                    unmatched_registrations = ?,
                    failed_events = ?,
                    failure_summary = ?
                WHERE singleton = TRUE
            """.trimIndent(),
            Timestamp.from(at),
            summary.eventsScanned,
            summary.creditsAwarded,
            summary.duplicateCredits,
            summary.unmatchedRegistrations,
            summary.failedEvents,
            summary.failureSummary?.take(200),
        )
    }

    @Transactional
    fun recordFailed(
        at: Instant,
        failureSummary: String,
    ) {
        jdbcTemplate.update(
            """
                UPDATE delta_scoring_sync_status
                SET last_attempt_at = ?,
                    outcome = 'FAILED',
                    events_scanned = 0,
                    credits_awarded = 0,
                    duplicate_credits = 0,
                    unmatched_registrations = 0,
                    failed_events = 0,
                    failure_summary = ?
                WHERE singleton = TRUE
            """.trimIndent(),
            Timestamp.from(at),
            failureSummary.take(200),
        )
    }
}
