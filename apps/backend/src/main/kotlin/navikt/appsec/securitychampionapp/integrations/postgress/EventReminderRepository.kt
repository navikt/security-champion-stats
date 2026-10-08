package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.events.EventReminderStore
import navikt.appsec.securitychampionapp.app.events.ReminderDelivery
import navikt.appsec.securitychampionapp.app.events.ReminderDeliveryStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class EventReminderRepository(private val jdbc: JdbcTemplate) : EventReminderStore {
    override fun deliveries(eventId: UUID): List<ReminderDelivery> = jdbc.query(
        "SELECT participant_id, status, next_attempt_at FROM event_reminder_deliveries WHERE event_id = ?",
        { rs, _ ->
            ReminderDelivery(
                rs.getObject("participant_id", UUID::class.java),
                ReminderDeliveryStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("next_attempt_at").toInstant(),
            )
        },
        eventId,
    )

    override fun claim(eventId: UUID, participantId: UUID, slackUserId: String, deliveryId: UUID): Boolean =
        jdbc.update(
            """
                INSERT INTO event_reminder_deliveries (id, event_id, participant_id, slack_user_id, status)
                SELECT ?, ?, id, ?, 'SENDING' FROM program_participants WHERE id = ? AND status = 'ACTIVE'
                ON CONFLICT (event_id, participant_id) DO UPDATE
                SET id = EXCLUDED.id, slack_user_id = EXCLUDED.slack_user_id, status = 'SENDING',
                    message_ts = NULL, updated_at = NOW()
                WHERE event_reminder_deliveries.status = 'FAILED'
                    AND event_reminder_deliveries.next_attempt_at <= NOW()
            """.trimIndent(),
            deliveryId, eventId, slackUserId, participantId,
        ) == 1

    override fun finish(deliveryId: UUID, status: ReminderDeliveryStatus, messageTs: String?, retryAfter: Duration) {
        check(jdbc.update(
            """
                UPDATE event_reminder_deliveries SET status = ?, message_ts = ?,
                    next_attempt_at = NOW() + (? * INTERVAL '1 millisecond'), updated_at = NOW()
                WHERE id = ? AND status = 'SENDING'
            """.trimIndent(),
            status.name, messageTs, retryAfter.toMillis(), deliveryId,
        ) == 1) { "Event reminder delivery no longer exists or changed" }
    }
}
