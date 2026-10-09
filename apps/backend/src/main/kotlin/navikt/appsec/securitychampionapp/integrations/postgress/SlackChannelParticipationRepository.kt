package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.membership.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
class SlackChannelParticipationRepository(
    private val jdbc: JdbcTemplate,
    private val transaction: TransactionTemplate,
) : SlackChannelParticipationStore {
    override fun observations(channelId: String): Map<UUID, ChannelObservation> =
        jdbc.query(
            """
                SELECT participant_id, slack_user_id, present, absent_since
                FROM slack_channel_participation
                WHERE channel_id = ?
            """.trimIndent(),
            { rs, _ ->
                ChannelObservation(
                    rs.getObject("participant_id", UUID::class.java),
                    rs.getString("slack_user_id"),
                    rs.getObject("present") as Boolean?,
                    rs.getTimestamp("absent_since")?.toInstant(),
                )
            },
            channelId,
        ).associateBy { it.participantId }

    override fun apply(channelId: String, checkedAt: Instant, plan: ChannelCheckPlan): ChannelCheckChanges =
        requireNotNull(transaction.execute {
            val deactivated = plan.departures.filter { (participantId, slackUserId) ->
                deactivate(channelId, participantId, slackUserId)
            }.keys
            val reactivated = plan.returns.filter(::reactivate).toSet()

            val observed = plan.observations.map { it.participantId }.toSet()
            jdbc.queryForList("SELECT participant_id FROM slack_channel_participation", UUID::class.java)
                .filterNot { it in observed }
                .forEach { jdbc.update("DELETE FROM slack_channel_participation WHERE participant_id = ?", it) }
            plan.observations.forEach { observation ->
                jdbc.update(
                    """
                        INSERT INTO slack_channel_participation
                            (participant_id, channel_id, slack_user_id, present, absent_since, checked_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (participant_id) DO UPDATE SET
                            channel_id = EXCLUDED.channel_id,
                            slack_user_id = EXCLUDED.slack_user_id,
                            present = EXCLUDED.present,
                            absent_since = EXCLUDED.absent_since,
                            checked_at = EXCLUDED.checked_at
                    """.trimIndent(),
                    observation.participantId,
                    channelId,
                    observation.slackUserId,
                    observation.present,
                    observation.absentSince?.let(Timestamp::from),
                    Timestamp.from(checkedAt),
                )
            }
            jdbc.update(
                """
                    DELETE FROM slack_channel_departure_notices
                    WHERE status IN ('SENT', 'CANCELLED') AND updated_at < NOW() - INTERVAL '12 months'
                """.trimIndent(),
            )
            ChannelCheckChanges(deactivated, reactivated)
        })

    private fun deactivate(channelId: String, participantId: UUID, slackUserId: String): Boolean {
        val changed = jdbc.update(
            """
                UPDATE program_participants
                SET status = 'DEACTIVATED', deactivation_reason = 'SLACK_CHANNEL_DEPARTURE', updated_at = NOW()
                WHERE id = ? AND status = 'ACTIVE'
            """.trimIndent(),
            participantId,
        ) == 1
        if (!changed) return false
        recordStatusChange(participantId, "ACTIVE", "DEACTIVATED")
        cancelPendingNotices(participantId)
        jdbc.update(
            """
                INSERT INTO slack_channel_departure_notices (participant_id, channel_id, slack_user_id)
                VALUES (?, ?, ?)
            """.trimIndent(),
            participantId, channelId, slackUserId,
        )
        return true
    }

    private fun reactivate(participantId: UUID): Boolean {
        val changed = jdbc.update(
            """
                UPDATE program_participants
                SET status = 'ACTIVE', deactivation_reason = NULL, updated_at = NOW()
                WHERE id = ? AND status = 'DEACTIVATED' AND deactivation_reason = 'SLACK_CHANNEL_DEPARTURE'
            """.trimIndent(),
            participantId,
        ) == 1
        if (!changed) return false
        recordStatusChange(participantId, "DEACTIVATED", "ACTIVE")
        cancelPendingNotices(participantId)
        return true
    }

    private fun recordStatusChange(participantId: UUID, before: String, after: String) {
        jdbc.update(
            """
                INSERT INTO program_participant_audit (
                    participant_id, actor_nav_no_email, action, before_values, after_values
                ) VALUES (
                    ?, NULL, 'PARTICIPATION_STATUS_CHANGED',
                    jsonb_build_object('status', ?::text),
                    jsonb_build_object('status', ?::text, 'reason', 'SLACK_CHANNEL_DEPARTURE')
                )
            """.trimIndent(),
            participantId, before, after,
        )
    }

    private fun cancelPendingNotices(participantId: UUID) {
        jdbc.update(
            """
                UPDATE slack_channel_departure_notices SET status = 'CANCELLED', updated_at = NOW()
                WHERE participant_id = ? AND status = 'PENDING'
            """.trimIndent(),
            participantId,
        )
    }

    override fun recoverInterruptedNotices(channelId: String) {
        jdbc.update(
            """
                UPDATE slack_channel_departure_notices SET status = 'UNCERTAIN', updated_at = NOW()
                WHERE channel_id = ? AND status = 'SENDING'
            """.trimIndent(),
            channelId,
        )
    }

    override fun dueNotices(channelId: String): List<ChannelDepartureNotice> =
        jdbc.query(
            """
                SELECT id, participant_id, slack_user_id, status
                FROM slack_channel_departure_notices
                WHERE channel_id = ? AND status = 'PENDING' AND next_attempt_at <= NOW()
                ORDER BY created_at, id
            """.trimIndent(),
            { rs, _ ->
                ChannelDepartureNotice(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("participant_id", UUID::class.java),
                    rs.getString("slack_user_id"),
                    ChannelNoticeStatus.valueOf(rs.getString("status")),
                )
            },
            channelId,
        )

    override fun updateNotice(id: UUID, status: ChannelNoticeStatus, messageTs: String?) {
        check(jdbc.update(
            """
                UPDATE slack_channel_departure_notices
                SET status = ?, message_ts = ?, updated_at = NOW() WHERE id = ?
            """.trimIndent(),
            status.name, messageTs, id,
        ) == 1) { "Slack channel departure notice no longer exists" }
    }

    override fun deferNotice(id: UUID, retryAfter: Duration) {
        require(!retryAfter.isNegative && !retryAfter.isZero) { "Delivery retry delay must be positive" }
        check(jdbc.update(
            """
                UPDATE slack_channel_departure_notices
                SET status = 'PENDING', next_attempt_at = NOW() + (? * INTERVAL '1 millisecond'), updated_at = NOW()
                WHERE id = ?
            """.trimIndent(),
            retryAfter.toMillis(), id,
        ) == 1) { "Slack channel departure notice no longer exists" }
    }

    override fun outstandingNotices(channelId: String): Int =
        jdbc.queryForObject(
            """
                SELECT COUNT(*) FROM slack_channel_departure_notices
                WHERE channel_id = ? AND status IN ('PENDING', 'SENDING', 'UNCERTAIN')
            """.trimIndent(),
            Int::class.java,
            channelId,
        ) ?: 0

    fun attentionItems(channelId: String): List<ChannelParticipantAttention> =
        jdbc.query(
            """
                SELECT participant.id, participant.fullname, participant.email, participant.deactivation_reason,
                    channel.present, channel.absent_since, notice.status AS notice_status
                FROM slack_channel_participation AS channel
                JOIN program_participants AS participant ON participant.id = channel.participant_id
                LEFT JOIN LATERAL (
                    SELECT status FROM slack_channel_departure_notices
                    WHERE participant_id = participant.id
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                ) AS notice ON participant.deactivation_reason = 'SLACK_CHANNEL_DEPARTURE'
                WHERE channel.channel_id = ?
                    AND (
                        participant.deactivation_reason = 'SLACK_CHANNEL_DEPARTURE'
                        OR (participant.status = 'ACTIVE' AND channel.present IS DISTINCT FROM TRUE)
                    )
                ORDER BY participant.fullname, participant.email
            """.trimIndent(),
            { rs, _ ->
                val present = rs.getObject("present") as Boolean?
                ChannelParticipantAttention(
                    participantId = rs.getObject("id", UUID::class.java),
                    name = rs.getString("fullname"),
                    email = rs.getString("email"),
                    category = when {
                        rs.getString("deactivation_reason") != null -> ChannelAttentionCategory.DEACTIVATED_AFTER_LEAVING
                        present == null -> ChannelAttentionCategory.IDENTITY_UNRESOLVED
                        else -> ChannelAttentionCategory.NOT_IN_CHANNEL
                    },
                    absentSince = rs.getTimestamp("absent_since")?.toInstant(),
                    notificationStatus = rs.getString("notice_status")?.let(ChannelNoticeStatus::valueOf),
                )
            },
            channelId,
        )

    fun status(channelId: String): ChannelCheckStatus? =
        jdbc.query(
            """
                SELECT last_attempt_at, last_success_at, outcome, failure_summary
                FROM slack_channel_participation_status WHERE channel_id = ?
            """.trimIndent(),
            { rs, _ ->
                ChannelCheckStatus(
                    rs.getTimestamp("last_attempt_at").toInstant(),
                    rs.getTimestamp("last_success_at")?.toInstant(),
                    rs.getString("outcome"),
                    rs.getString("failure_summary"),
                )
            },
            channelId,
        ).firstOrNull()

    fun recordStarted(channelId: String, at: Instant) {
        jdbc.update(
            """
                INSERT INTO slack_channel_participation_status (channel_id, last_attempt_at, outcome)
                VALUES (?, ?, 'RUNNING')
                ON CONFLICT (channel_id) DO UPDATE SET
                    last_attempt_at = EXCLUDED.last_attempt_at, outcome = 'RUNNING', failure_summary = NULL
            """.trimIndent(),
            channelId, Timestamp.from(at),
        )
    }

    fun recordCompleted(channelId: String, at: Instant, partial: Boolean) {
        jdbc.update(
            """
                UPDATE slack_channel_participation_status
                SET last_success_at = ?, outcome = ?, failure_summary = ?
                WHERE channel_id = ?
            """.trimIndent(),
            Timestamp.from(at),
            if (partial) "PARTIAL_FAILURE" else "SUCCEEDED",
            if (partial) "Departure notices need attention" else null,
            channelId,
        )
    }

    fun recordFailed(channelId: String, at: Instant, summary: String) {
        jdbc.update(
            """
                INSERT INTO slack_channel_participation_status (channel_id, last_attempt_at, outcome, failure_summary)
                VALUES (?, ?, 'FAILED', ?)
                ON CONFLICT (channel_id) DO UPDATE SET
                    last_attempt_at = EXCLUDED.last_attempt_at, outcome = 'FAILED',
                    failure_summary = EXCLUDED.failure_summary
            """.trimIndent(),
            channelId, Timestamp.from(at), summary.take(200),
        )
    }
}
