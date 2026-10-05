package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.scoring.SlackSyncSummary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

enum class SlackSyncOutcome {
    RUNNING,
    SUCCEEDED,
    FAILED,
}

data class SlackSyncStatusRecord(
    val lastAttemptAt: Instant,
    val lastSuccessAt: Instant?,
    val outcome: SlackSyncOutcome,
    val messagesScanned: Int,
    val creditsAwarded: Int,
    val duplicateCredits: Int,
    val unmappedAuthors: Int,
    val failureSummary: String?,
)

@Repository
class SlackScoringStatusRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val rowMapper = RowMapper { rs, _ ->
        SlackSyncStatusRecord(
            lastAttemptAt = rs.getTimestamp("last_attempt_at").toInstant(),
            lastSuccessAt = rs.getTimestamp("last_success_at")?.toInstant(),
            outcome = SlackSyncOutcome.valueOf(rs.getString("outcome")),
            messagesScanned = rs.getInt("messages_scanned"),
            creditsAwarded = rs.getInt("credits_awarded"),
            duplicateCredits = rs.getInt("duplicate_credits"),
            unmappedAuthors = rs.getInt("unmapped_authors"),
            failureSummary = rs.getString("failure_summary"),
        )
    }

    fun find(): SlackSyncStatusRecord? =
        jdbcTemplate.query(
            """
                SELECT last_attempt_at, last_success_at, outcome, messages_scanned,
                    credits_awarded, duplicate_credits, unmapped_authors, failure_summary
                FROM slack_scoring_sync_status
                WHERE singleton = TRUE
            """.trimIndent(),
            rowMapper,
        ).firstOrNull()

    @Transactional
    fun recordStarted(at: Instant) {
        jdbcTemplate.update(
            """
                INSERT INTO slack_scoring_sync_status (singleton, last_attempt_at, outcome)
                VALUES (TRUE, ?, 'RUNNING')
                ON CONFLICT (singleton) DO UPDATE SET
                    last_attempt_at = EXCLUDED.last_attempt_at,
                    outcome = 'RUNNING',
                    messages_scanned = 0,
                    credits_awarded = 0,
                    duplicate_credits = 0,
                    unmapped_authors = 0,
                    failure_summary = NULL
            """.trimIndent(),
            Timestamp.from(at),
        )
    }

    @Transactional
    fun recordSucceeded(at: Instant, summary: SlackSyncSummary) {
        jdbcTemplate.update(
            """
                UPDATE slack_scoring_sync_status
                SET last_success_at = ?,
                    outcome = 'SUCCEEDED',
                    messages_scanned = ?,
                    credits_awarded = ?,
                    duplicate_credits = ?,
                    unmapped_authors = ?,
                    failure_summary = NULL
                WHERE singleton = TRUE
            """.trimIndent(),
            Timestamp.from(at),
            summary.messagesScanned,
            summary.creditsAwarded,
            summary.duplicateCredits,
            summary.unmappedAuthors,
        )
    }

    @Transactional
    fun recordFailed(at: Instant, failureSummary: String) {
        jdbcTemplate.update(
            """
                UPDATE slack_scoring_sync_status
                SET last_attempt_at = ?,
                    outcome = 'FAILED',
                    messages_scanned = 0,
                    credits_awarded = 0,
                    duplicate_credits = 0,
                    unmapped_authors = 0,
                    failure_summary = ?
                WHERE singleton = TRUE
            """.trimIndent(),
            Timestamp.from(at),
            failureSummary.take(200),
        )
    }
}
