package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.integrations.github.GitHubFailure
import navikt.appsec.securitychampionapp.integrations.github.GitHubIdentity
import navikt.appsec.securitychampionapp.integrations.github.GitHubIntegrationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class MappedGitHubParticipant(
    val accountId: Long,
    val participantId: UUID,
    val active: Boolean,
    val enrolledAt: Instant,
)

@Repository
class GitHubIdentityMappingRepository(private val jdbcTemplate: JdbcTemplate) {
    @Transactional
    fun refresh(identities: List<GitHubIdentity>, verifiedAt: Instant): Map<Long, MappedGitHubParticipant> {
        if (identities.groupBy { it.accountId }.any { it.value.size > 1 } ||
            identities.groupBy { it.email.lowercase() }.any { it.value.size > 1 }
        ) {
            throw GitHubIntegrationException(GitHubFailure.IDENTITY, "duplicateIdentity")
        }
        val participants = jdbcTemplate.query(
            "SELECT id, nav_no_email, status, created_at FROM program_participants FOR SHARE",
            { rs, _ ->
                rs.getString("nav_no_email").lowercase() to MappedGitHubParticipant(
                    accountId = 0,
                    participantId = rs.getObject("id", UUID::class.java),
                    active = rs.getString("status") == "ACTIVE",
                    enrolledAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
        )
        if (participants.groupBy { it.first }.any { it.value.size > 1 }) {
            throw GitHubIntegrationException(GitHubFailure.IDENTITY, "duplicateParticipantEmail")
        }
        val byEmail = participants.toMap()
        val desired = identities.mapNotNull { identity ->
            byEmail[identity.email.lowercase()]?.let { identity to it.copy(accountId = identity.accountId) }
        }
        val current = jdbcTemplate.query(
            "SELECT github_account_id, participant_id FROM github_account_mappings",
            { rs, _ -> rs.getLong("github_account_id") to rs.getObject("participant_id", UUID::class.java) },
        )
        val desiredPairs = desired.map { it.first.accountId to it.second.participantId }.toSet()
        current.filterNot(desiredPairs::contains).forEach { (accountId, participantId) ->
            jdbcTemplate.update("DELETE FROM github_account_mappings WHERE github_account_id = ?", accountId)
            recordMappingAudit(participantId, "GITHUB_ACCOUNT_UNMAPPED", accountId, before = true)
        }
        val currentPairs = current.toSet()
        desired.forEach { (identity, participant) ->
            jdbcTemplate.update(
                """
                    INSERT INTO github_account_mappings
                        (github_account_id, github_login, participant_id, verified_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (github_account_id) DO UPDATE
                    SET github_login = EXCLUDED.github_login, verified_at = EXCLUDED.verified_at
                """.trimIndent(),
                identity.accountId,
                identity.login,
                participant.participantId,
                Timestamp.from(verifiedAt),
            )
            if ((identity.accountId to participant.participantId) !in currentPairs) {
                recordMappingAudit(
                    participant.participantId, "GITHUB_ACCOUNT_MAPPED", identity.accountId, before = false,
                )
            }
        }
        return desired.associate { it.first.accountId to it.second }
    }

    private fun recordMappingAudit(participantId: UUID, action: String, accountId: Long, before: Boolean) {
        jdbcTemplate.update(
            """
                INSERT INTO program_participant_audit
                    (participant_id, action, before_values, after_values)
                VALUES (?, ?, jsonb_build_object('githubAccountId', ?::bigint),
                    jsonb_build_object('githubAccountId', ?::bigint))
            """.trimIndent(),
            participantId,
            action,
            if (before) accountId else null,
            if (before) null else accountId,
        )
    }
}
