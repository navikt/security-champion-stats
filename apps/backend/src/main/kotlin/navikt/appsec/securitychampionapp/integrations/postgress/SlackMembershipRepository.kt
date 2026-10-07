package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.membership.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID

@Repository
class SlackMembershipRepository(
    private val jdbc: JdbcTemplate,
    private val transaction: TransactionTemplate,
) : SlackMembershipStore {
    override fun observe(usergroupId: String, activeUsers: Map<UUID, String>) {
        require(activeUsers.isNotEmpty()) { "Cannot baseline an empty Slack membership" }
        transaction.executeWithoutResult {
            val initialized = jdbc.update(
                "INSERT INTO slack_membership_baselines (usergroup_id) VALUES (?) ON CONFLICT DO NOTHING",
                usergroupId,
            ) == 1
            jdbc.queryForObject(
                "SELECT usergroup_id FROM slack_membership_baselines WHERE usergroup_id = ? FOR UPDATE",
                String::class.java,
                usergroupId,
            )
            val previous = jdbc.query(
                "SELECT participant_id, slack_user_id FROM slack_membership_snapshot WHERE usergroup_id = ?",
                { rs, _ -> rs.getObject("participant_id", UUID::class.java) to rs.getString("slack_user_id") },
                usergroupId,
            ).toMap()
            if (!initialized) {
                (activeUsers.keys - previous.keys).forEach { id ->
                    enqueue(usergroupId, id, activeUsers.getValue(id), MembershipAnnouncementKind.WELCOME)
                }
                (previous.keys - activeUsers.keys).forEach { id ->
                    enqueue(usergroupId, id, previous.getValue(id), MembershipAnnouncementKind.REMOVAL)
                }
            }
            jdbc.update("DELETE FROM slack_membership_snapshot WHERE usergroup_id = ?", usergroupId)
            activeUsers.forEach { (participantId, slackUserId) ->
                jdbc.update(
                    """
                        INSERT INTO slack_membership_snapshot (usergroup_id, participant_id, slack_user_id)
                        VALUES (?, ?, ?)
                    """.trimIndent(),
                    usergroupId, participantId, slackUserId,
                )
            }
            jdbc.update(
                """
                    DELETE FROM slack_membership_announcements
                    WHERE usergroup_id = ? AND status IN ('SENT', 'SUPPRESSED', 'CANCELLED')
                        AND updated_at < NOW() - INTERVAL '12 months'
                """.trimIndent(),
                usergroupId,
            )
        }
    }

    private fun enqueue(group: String, participantId: UUID, userId: String, kind: MembershipAnnouncementKind) {
        jdbc.update(
            """
                UPDATE slack_membership_announcements SET status = 'CANCELLED', updated_at = NOW()
                WHERE usergroup_id = ? AND participant_id = ? AND status = 'PENDING'
            """.trimIndent(),
            group, participantId,
        )
        jdbc.update(
            """
                INSERT INTO slack_membership_announcements (usergroup_id, participant_id, slack_user_id, kind)
                VALUES (?, ?, ?, ?)
            """.trimIndent(),
            group, participantId, userId, kind.name,
        )
    }

    override fun announcements(usergroupId: String): List<MembershipAnnouncement> =
        jdbc.query(
            """
                SELECT id, participant_id, slack_user_id, kind, status, next_attempt_at
                FROM slack_membership_announcements
                WHERE usergroup_id = ? AND status IN ('PENDING', 'SENDING', 'UNCERTAIN')
                ORDER BY created_at, id
            """.trimIndent(),
            { rs, _ ->
                MembershipAnnouncement(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("participant_id", UUID::class.java),
                    rs.getString("slack_user_id"),
                    MembershipAnnouncementKind.valueOf(rs.getString("kind")),
                    MembershipDeliveryStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("next_attempt_at").toInstant(),
                )
            },
            usergroupId,
        )

    override fun updateDelivery(id: UUID, status: MembershipDeliveryStatus, messageTs: String?) {
        check(jdbc.update(
            """
                UPDATE slack_membership_announcements
                SET status = ?, message_ts = ?, updated_at = NOW() WHERE id = ?
            """.trimIndent(),
            status.name, messageTs, id,
        ) == 1) { "Slack membership announcement no longer exists" }
    }

    override fun recoverInterruptedDeliveries(usergroupId: String) {
        jdbc.update(
            """
                UPDATE slack_membership_announcements SET status = 'UNCERTAIN', updated_at = NOW()
                WHERE usergroup_id = ? AND status = 'SENDING'
            """.trimIndent(),
            usergroupId,
        )
    }

    override fun deferDelivery(id: UUID, retryAfter: Duration) {
        require(!retryAfter.isNegative && !retryAfter.isZero) { "Delivery retry delay must be positive" }
        check(jdbc.update(
            """
                UPDATE slack_membership_announcements
                SET status = 'PENDING', next_attempt_at = NOW() + (? * INTERVAL '1 millisecond'), updated_at = NOW()
                WHERE id = ?
            """.trimIndent(),
            retryAfter.toMillis(), id,
        ) == 1) { "Slack membership announcement no longer exists" }
    }

    fun resolveUncertain(usergroupId: String, id: UUID, retry: Boolean): Boolean =
        jdbc.update(
            """
                UPDATE slack_membership_announcements SET status = ?, next_attempt_at = NOW(), updated_at = NOW()
                WHERE usergroup_id = ? AND id = ? AND status = 'UNCERTAIN'
            """.trimIndent(),
            if (retry) "PENDING" else "SUPPRESSED", usergroupId, id,
        ) == 1
}
