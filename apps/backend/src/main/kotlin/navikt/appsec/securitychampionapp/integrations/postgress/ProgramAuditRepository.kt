package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ParticipantHistoryEntry
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditEntry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class ProgramAuditRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val entryMapper = RowMapper { rs, _ ->
        val detailValues = objectMapper.readValue(rs.getString("details"), Map::class.java)
        ProgramAuditEntry(
            id = rs.getObject("id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            action = rs.getString("action"),
            outcome = AuditOutcome.valueOf(rs.getString("outcome")),
            actorNavNoEmail = rs.getString("actor_nav_no_email"),
            targetParticipantId = rs.getObject("target_participant_id", UUID::class.java),
            targetParticipantName = rs.getString("target_participant_name"),
            correlationId = rs.getObject("correlation_id", UUID::class.java),
            details = detailValues.entries.associate { it.key.toString() to it.value.toString() },
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insert(
        action: String,
        outcome: AuditOutcome,
        actorNavNoEmail: String?,
        targetParticipantId: UUID?,
        correlationId: UUID?,
        details: Map<String, Any?>,
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO program_audit_events (
                    action, outcome, actor_nav_no_email, target_participant_id, correlation_id, details
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb)
            """.trimIndent(),
            action,
            outcome.name,
            actorNavNoEmail,
            targetParticipantId,
            correlationId,
            objectMapper.writeValueAsString(details),
        )
    }

    fun adminPage(
        query: String?,
        page: Int,
        size: Int,
        category: String? = null,
    ): Pair<List<ProgramAuditEntry>, Long> {
        val pattern = query?.let { "%${escapeLike(it)}%" }
        val total = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM program_audit_events
                WHERE (CAST(? AS text) IS NULL OR (
                    action ILIKE ? ESCAPE '\'
                    OR outcome ILIKE ? ESCAPE '\'
                    OR COALESCE(actor_nav_no_email, '') ILIKE ? ESCAPE '\'
                    OR details::text ILIKE ? ESCAPE '\'
                    OR COALESCE(correlation_id::text, '') ILIKE ? ESCAPE '\'
                    OR COALESCE(target_participant_id::text, '') ILIKE ? ESCAPE '\'
                ))
                AND (
                    CAST(? AS text) IS NULL
                    OR (? = 'syncs' AND action ILIKE '%SYNC%')
                    OR (? = 'credits' AND action ILIKE '%CREDIT%')
                    OR (? = 'admin' AND action NOT ILIKE '%SYNC%' AND action NOT ILIKE '%CREDIT%')
                )
            """.trimIndent(),
            Long::class.javaObjectType,
            pattern,
            pattern,
            pattern,
            pattern,
            pattern,
            pattern,
            pattern,
            category,
            category,
            category,
            category,
        ) ?: 0L
        val rows = jdbcTemplate.query(
            """
                SELECT event.id, event.created_at, event.action, event.outcome, event.actor_nav_no_email,
                    event.target_participant_id, participant.fullname AS target_participant_name,
                    event.correlation_id, event.details
                FROM program_audit_events AS event
                LEFT JOIN program_participants AS participant
                    ON participant.id = event.target_participant_id
                WHERE (CAST(? AS text) IS NULL OR (
                    event.action ILIKE ? ESCAPE '\'
                    OR event.outcome ILIKE ? ESCAPE '\'
                    OR COALESCE(event.actor_nav_no_email, '') ILIKE ? ESCAPE '\'
                    OR event.details::text ILIKE ? ESCAPE '\'
                    OR COALESCE(event.correlation_id::text, '') ILIKE ? ESCAPE '\'
                    OR COALESCE(event.target_participant_id::text, '') ILIKE ? ESCAPE '\'
                ))
                AND (
                    CAST(? AS text) IS NULL
                    OR (? = 'syncs' AND event.action ILIKE '%SYNC%')
                    OR (? = 'credits' AND event.action ILIKE '%CREDIT%')
                    OR (? = 'admin' AND event.action NOT ILIKE '%SYNC%' AND event.action NOT ILIKE '%CREDIT%')
                )
                ORDER BY event.created_at DESC, event.id DESC
                LIMIT ? OFFSET ?
            """.trimIndent(),
            entryMapper,
            pattern,
            pattern,
            pattern,
            pattern,
            pattern,
            pattern,
            pattern,
            category,
            category,
            category,
            category,
            size,
            page.toLong() * size,
        )
        return rows to total
    }

    fun participantHistory(participantId: UUID): List<ParticipantHistoryEntry> =
        jdbcTemplate.query(
            """
                SELECT
                    'membership:' || id AS id,
                    created_at AS occurred_at,
                    'MEMBERSHIP' AS type,
                    action,
                    after_values ->> 'status' AS status,
                    NULL::text AS credit_type,
                    NULL::integer AS points,
                    NULL::text AS source_reference,
                    NULL::text AS reason
                FROM program_participant_audit
                WHERE participant_id = ?
                    AND action = 'PARTICIPATION_STATUS_CHANGED'
                    AND created_at >= (SELECT started_at FROM program_audit_rollout WHERE singleton = TRUE)
                UNION ALL
                SELECT
                    id::text,
                    created_at,
                    'MEMBERSHIP',
                    action,
                    details ->> 'status',
                    NULL::text,
                    NULL::integer,
                    NULL::text,
                    NULL::text
                FROM program_audit_events
                WHERE target_participant_id = ?
                    AND outcome = 'SUCCEEDED'
                    AND action IN ('PARTICIPANT_ENROLLED', 'PARTICIPANT_LEFT', 'PARTICIPANT_REJOINED')
                UNION ALL
                SELECT
                    credit.id::text,
                    credit.awarded_at,
                    'CREDIT',
                    'CREDIT_AWARDED',
                    NULL::text,
                    credit.credit_type,
                    credit.points,
                    credit.source_reference,
                    NULL::text
                FROM activity_credits AS credit
                WHERE credit.participant_id = ?
                    AND credit.awarded_at >= (SELECT started_at FROM program_audit_rollout WHERE singleton = TRUE)
                UNION ALL
                SELECT
                    adjustment.id::text,
                    adjustment.created_at,
                    'ADJUSTMENT',
                    'POINTS_ADJUSTED',
                    NULL::text,
                    NULL::text,
                    adjustment.points_delta,
                    NULL::text,
                    adjustment.reason
                FROM point_adjustments AS adjustment
                WHERE adjustment.participant_id = ?
                    AND adjustment.created_at >= (SELECT started_at FROM program_audit_rollout WHERE singleton = TRUE)
                ORDER BY occurred_at DESC, id DESC
            """.trimIndent(),
            { rs, _ ->
                ParticipantHistoryEntry(
                    id = rs.getString("id"),
                    occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                    type = rs.getString("type"),
                    action = rs.getString("action"),
                    status = rs.getString("status"),
                    creditType = rs.getString("credit_type"),
                    points = rs.getObject("points", Int::class.javaObjectType),
                    sourceReference = rs.getString("source_reference"),
                    reason = rs.getString("reason"),
                )
            },
            participantId,
            participantId,
            participantId,
            participantId,
        )

    fun participantExists(participantId: UUID): Boolean =
        jdbcTemplate.query(
            "SELECT id FROM program_participants WHERE id = ?",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            participantId,
        ).isNotEmpty()

    fun participantIdForSlackUserId(slackUserId: String): UUID? =
        jdbcTemplate.query(
            "SELECT participant_id FROM slack_account_mappings WHERE slack_user_id = ?",
            { rs, _ -> rs.getObject("participant_id", UUID::class.java) },
            slackUserId,
        ).firstOrNull()

    fun participantIdForNavNoEmail(navNoEmail: String): UUID? =
        jdbcTemplate.query(
            "SELECT id FROM program_participants WHERE nav_no_email = ?",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            navNoEmail,
        ).firstOrNull()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun deleteExpiredOperationalEvents(now: Instant): Int =
        jdbcTemplate.queryForObject(
            """
                WITH expired_participant_operations AS (
                    DELETE FROM program_participant_audit
                    WHERE created_at < ?::timestamptz - INTERVAL '12 months'
                        AND action <> 'PARTICIPATION_STATUS_CHANGED'
                    RETURNING id
                ),
                expired_scoring_operations AS (
                    DELETE FROM program_scoring_audit
                    WHERE created_at < ?::timestamptz - INTERVAL '12 months'
                    RETURNING id
                ),
                expired_program_operations AS (
                DELETE FROM program_audit_events
                WHERE created_at < ?::timestamptz - INTERVAL '12 months'
                    AND NOT (
                        outcome = 'SUCCEEDED'
                        AND action IN ('PARTICIPANT_ENROLLED', 'PARTICIPANT_LEFT', 'PARTICIPANT_REJOINED')
                    )
                RETURNING id
                )
                SELECT (
                    (SELECT COUNT(*) FROM expired_participant_operations)
                    + (SELECT COUNT(*) FROM expired_scoring_operations)
                    + (SELECT COUNT(*) FROM expired_program_operations)
                )::integer
            """.trimIndent(),
            Int::class.java,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now),
        ) ?: 0

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
