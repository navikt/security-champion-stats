package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.scoring.GitHubSyncSummary
import navikt.appsec.securitychampionapp.app.scoring.GitHubSyncStatusView
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class GitHubScoringStatusRepository(private val jdbcTemplate: JdbcTemplate) {
    fun find(enabled: Boolean): GitHubSyncStatusView =
        jdbcTemplate.query(
            "SELECT * FROM github_scoring_sync_status WHERE singleton = TRUE",
            { rs, _ ->
                GitHubSyncStatusView(
                    enabled,
                    rs.getTimestamp("last_attempt_at").toInstant(),
                    rs.getTimestamp("last_success_at")?.toInstant(),
                    rs.getString("outcome"),
                    rs.getInt("contributions_scanned"),
                    rs.getInt("credits_awarded"),
                    rs.getInt("duplicate_credits"),
                    rs.getInt("unmapped_authors"),
                    rs.getString("failure_summary"),
                )
            },
        ).firstOrNull() ?: GitHubSyncStatusView(enabled, null, null, null, 0, 0, 0, 0, null)

    fun recordStarted(at: Instant) {
        jdbcTemplate.update(
            """
                INSERT INTO github_scoring_sync_status (singleton, last_attempt_at, outcome)
                VALUES (TRUE, ?, 'RUNNING')
                ON CONFLICT (singleton) DO UPDATE SET last_attempt_at = EXCLUDED.last_attempt_at,
                    outcome = 'RUNNING', contributions_scanned = 0, credits_awarded = 0,
                    duplicate_credits = 0, unmapped_authors = 0, failure_summary = NULL
            """.trimIndent(),
            Timestamp.from(at),
        )
    }

    fun recordSucceeded(at: Instant, summary: GitHubSyncSummary) {
        jdbcTemplate.update(
            """
                UPDATE github_scoring_sync_status
                SET last_success_at = ?, outcome = 'SUCCEEDED', contributions_scanned = ?,
                    credits_awarded = ?, duplicate_credits = ?, unmapped_authors = ?, failure_summary = NULL
                WHERE singleton = TRUE
            """.trimIndent(),
            Timestamp.from(at),
            summary.contributionsScanned,
            summary.creditsAwarded,
            summary.duplicateCredits,
            summary.unmappedAuthors,
        )
    }

    fun recordFailed(summary: String) {
        jdbcTemplate.update(
            """
                UPDATE github_scoring_sync_status SET outcome = 'FAILED', failure_summary = ?
                WHERE singleton = TRUE
            """.trimIndent(),
            summary.take(200),
        )
    }
}
