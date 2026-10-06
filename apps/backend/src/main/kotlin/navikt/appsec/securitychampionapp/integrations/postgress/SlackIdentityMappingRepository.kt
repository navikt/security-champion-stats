package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.MappedSlackParticipant
import navikt.appsec.securitychampionapp.app.scoring.SlackAccountMapping
import navikt.appsec.securitychampionapp.app.scoring.UnmappedSlackAuthor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class SlackIdentityMappingRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val auditService: ProgramAuditService? = null,
) {
    fun mappingOverview(): Pair<List<SlackAccountMapping>, List<UnmappedSlackAuthor>> {
        val mappings = jdbcTemplate.query(
            """
                SELECT mapping.slack_user_id, participant.id, participant.fullname, participant.email,
                    mapping.created_at
                FROM slack_account_mappings AS mapping
                JOIN program_participants AS participant ON participant.id = mapping.participant_id
                ORDER BY LOWER(participant.fullname), mapping.slack_user_id
            """.trimIndent(),
            { rs, _ ->
                SlackAccountMapping(
                    slackUserId = rs.getString("slack_user_id"),
                    participantId = rs.getObject("id", UUID::class.java),
                    participantName = rs.getString("fullname"),
                    participantEmail = rs.getString("email"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
        )
        val unmappedAuthors = jdbcTemplate.query(
            """
                SELECT slack_user_id, first_seen_at, last_seen_at
                FROM slack_unmapped_authors
                ORDER BY first_seen_at, slack_user_id
            """.trimIndent(),
            { rs, _ ->
                UnmappedSlackAuthor(
                    slackUserId = rs.getString("slack_user_id"),
                    firstSeenAt = rs.getTimestamp("first_seen_at").toInstant(),
                    lastSeenAt = rs.getTimestamp("last_seen_at").toInstant(),
                )
            },
        )
        return mappings to unmappedAuthors
    }

    fun mappedParticipants(): Map<String, MappedSlackParticipant> =
        jdbcTemplate.query(
            """
                SELECT mapping.slack_user_id, participant.id, participant.status, participant.created_at
                FROM slack_account_mappings AS mapping
                JOIN program_participants AS participant ON participant.id = mapping.participant_id
            """.trimIndent(),
            { rs, _ ->
                MappedSlackParticipant(
                    slackUserId = rs.getString("slack_user_id"),
                    participantId = rs.getObject("id", UUID::class.java),
                    active = rs.getString("status") == "ACTIVE",
                    enrolledAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
        ).associateBy(MappedSlackParticipant::slackUserId)

    @Transactional
    fun addMapping(
        slackUserId: String,
        participantId: UUID,
        actorNavNoEmail: String,
    ): Boolean {
        val inserted = jdbcTemplate.update(
            """
                INSERT INTO slack_account_mappings (slack_user_id, participant_id, created_by_nav_no_email)
                SELECT ?, participant.id, ?
                FROM program_participants AS participant
                WHERE participant.id = ?
            """.trimIndent(),
            slackUserId,
            actorNavNoEmail,
            participantId,
        )
        if (inserted == 0) return false

        jdbcTemplate.update("DELETE FROM slack_unmapped_authors WHERE slack_user_id = ?", slackUserId)
        jdbcTemplate.update(
            """
                INSERT INTO program_participant_audit (
                    participant_id, actor_nav_no_email, action, before_values, after_values
                ) VALUES (?, ?, 'SLACK_ACCOUNT_MAPPED', '{}'::jsonb, jsonb_build_object('slackUserId', ?))
            """.trimIndent(),
            participantId,
            actorNavNoEmail,
            slackUserId,
        )
        auditService?.record(
            "SLACK_ACCOUNT_MAPPED",
            AuditOutcome.SUCCEEDED,
            actorNavNoEmail,
            participantId,
        )
        return true
    }

    @Transactional
    fun removeMapping(slackUserId: String, actorNavNoEmail: String): Boolean {
        val participantId = jdbcTemplate.query(
            """
                DELETE FROM slack_account_mappings
                WHERE slack_user_id = ?
                RETURNING participant_id
            """.trimIndent(),
            { rs, _ -> rs.getObject("participant_id", UUID::class.java) },
            slackUserId,
        ).firstOrNull() ?: return false

        jdbcTemplate.update(
            """
                INSERT INTO slack_unmapped_authors (slack_user_id)
                VALUES (?)
                ON CONFLICT (slack_user_id) DO UPDATE SET last_seen_at = NOW()
            """.trimIndent(),
            slackUserId,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_participant_audit (
                    participant_id, actor_nav_no_email, action, before_values, after_values
                ) VALUES (?, ?, 'SLACK_ACCOUNT_UNMAPPED', jsonb_build_object('slackUserId', ?), '{}'::jsonb)
            """.trimIndent(),
            participantId,
            actorNavNoEmail,
            slackUserId,
        )
        auditService?.record(
            "SLACK_ACCOUNT_UNMAPPED",
            AuditOutcome.SUCCEEDED,
            actorNavNoEmail,
            participantId,
        )
        return true
    }

    @Transactional
    fun addMappingByNavNoEmail(
        slackUserId: String,
        navNoEmail: String,
        actor: String,
    ): Boolean {
        val participantId = jdbcTemplate.query(
            """
                INSERT INTO slack_account_mappings (slack_user_id, participant_id, created_by_nav_no_email)
                SELECT ?, participant.id, ?
                FROM program_participants AS participant
                WHERE LOWER(participant.nav_no_email) = LOWER(?)
                ON CONFLICT DO NOTHING
                RETURNING participant_id
            """.trimIndent(),
            { rs, _ -> rs.getObject("participant_id", UUID::class.java) },
            slackUserId,
            actor,
            navNoEmail,
        ).firstOrNull() ?: return false

        jdbcTemplate.update("DELETE FROM slack_unmapped_authors WHERE slack_user_id = ?", slackUserId)
        jdbcTemplate.update(
            """
                INSERT INTO program_participant_audit (
                    participant_id, actor_nav_no_email, action, before_values, after_values
                ) VALUES (?, ?, 'SLACK_ACCOUNT_MAPPED', '{}'::jsonb, jsonb_build_object('slackUserId', ?))
            """.trimIndent(),
            participantId,
            actor,
            slackUserId,
        )
        auditService?.record(
            "SLACK_ACCOUNT_AUTOMATICALLY_MAPPED",
            AuditOutcome.SUCCEEDED,
            targetParticipantId = participantId,
        )
        return true
    }

    fun recordUnmappedAuthor(slackUserId: String) {
        jdbcTemplate.update(
            """
                INSERT INTO slack_unmapped_authors (slack_user_id)
                VALUES (?)
                ON CONFLICT (slack_user_id) DO UPDATE SET last_seen_at = NOW()
            """.trimIndent(),
            slackUserId,
        )
    }

    fun syncCursor(channelId: String, firstObservedAt: Instant): Instant {
        jdbcTemplate.update(
            """
                INSERT INTO slack_scoring_sync_state (channel_id, last_synced_at)
                VALUES (?, ?)
                ON CONFLICT (channel_id) DO NOTHING
            """.trimIndent(),
            channelId,
            Timestamp.from(firstObservedAt),
        )
        return jdbcTemplate.queryForObject(
            "SELECT last_synced_at FROM slack_scoring_sync_state WHERE channel_id = ?",
            { rs, _ -> rs.getTimestamp("last_synced_at").toInstant() },
            channelId,
        ) ?: error("Slack scoring sync state was not created")
    }

    fun advanceSyncCursor(channelId: String, syncedThrough: Instant) {
        val updated = jdbcTemplate.update(
            "UPDATE slack_scoring_sync_state SET last_synced_at = ? WHERE channel_id = ?",
            Timestamp.from(syncedThrough),
            channelId,
        )
        check(updated == 1) { "Slack scoring sync state does not exist for the configured channel" }
    }
}
