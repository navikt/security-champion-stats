package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.feedback.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class PostgresFeedbackStore(private val jdbc: JdbcTemplate) : FeedbackStore {
    override fun currentSeason(): UUID = requireNotNull(jdbc.queryForObject(
        "SELECT id FROM program_seasons WHERE ends_on IS NULL FOR SHARE", UUID::class.java,
    ))

    override fun lockParticipant(id: UUID) {
        jdbc.query("SELECT id FROM program_participants WHERE id = ? FOR UPDATE", { rs, _ -> rs.getObject("id", UUID::class.java) }, id)
    }

    override fun activeParticipant(email: String): UUID? = jdbc.query(
        "SELECT id FROM program_participants WHERE nav_no_email = ? AND status = 'ACTIVE' FOR UPDATE",
        { rs, _ -> rs.getObject("id", UUID::class.java) }, email,
    ).singleOrNull()

    override fun submit(participantId: UUID, seasonId: UUID, request: SubmitFeedbackRequest, at: Instant) {
        jdbc.update(
            """INSERT INTO program_feedback(id, submitter_id, season_id, title, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING""",
            request.id, participantId, seasonId, request.title, request.description, Timestamp.from(at),
        )
    }

    private val query = """SELECT f.*, p.fullname, p.nav_no_email, p.status AS participant_status
        FROM program_feedback f JOIN program_participants p ON p.id = f.submitter_id"""
    private val rowMapper = RowMapper { rs, _ ->
        Feedback(
            id = rs.getObject("id", UUID::class.java),
            submitterId = rs.getObject("submitter_id", UUID::class.java),
            submitterName = rs.getString("fullname"), submitterEmail = rs.getString("nav_no_email"),
            submitterActive = rs.getString("participant_status") == "ACTIVE",
            seasonId = rs.getObject("season_id", UUID::class.java),
            title = rs.getString("title"), description = rs.getString("description"),
            status = FeedbackStatus.valueOf(rs.getString("status")), ownerEmail = rs.getString("owner_email"),
            scoreDecision = FeedbackScoreDecision.valueOf(rs.getString("score_decision")),
            creditId = rs.getObject("credit_id", UUID::class.java), version = rs.getLong("version"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    override fun find(id: UUID, lock: Boolean): Feedback? = jdbc.query(
        "$query WHERE f.id = ?" + if (lock) " FOR UPDATE OF f" else "", rowMapper, id,
    ).singleOrNull()

    override fun search(search: FeedbackSearch, actorEmail: String): List<Feedback> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (search.query.isNotEmpty()) {
            conditions += """(strpos(lower(f.title || ' ' || f.description || ' ' || p.fullname || ' ' || p.nav_no_email), lower(?)) > 0
                OR EXISTS (SELECT 1 FROM program_feedback_history h WHERE h.feedback_id = f.id
                    AND strpos(lower(h.note || ' ' || coalesce(h.ticket_url, '')), lower(?)) > 0))"""
            args += search.query
            args += search.query
        }
        search.status?.let { conditions += "f.status = ?"; args += it.name }
        search.scoreDecision?.let { conditions += "f.score_decision = ?"; args += it.name }
        when (search.owner) {
            "mine" -> { conditions += "f.owner_email = ?"; args += actorEmail }
            "unassigned" -> conditions += "f.owner_email IS NULL"
        }
        val where = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        args += search.offset
        return jdbc.query("$query$where ORDER BY f.created_at DESC, f.id DESC LIMIT 26 OFFSET ?", rowMapper, *args.toTypedArray())
    }

    override fun history(id: UUID): List<FeedbackHistory> = jdbc.query(
        "SELECT * FROM program_feedback_history WHERE feedback_id = ? ORDER BY id",
        { rs, _ -> FeedbackHistory(
            rs.getLong("id"), rs.getString("actor_email"), FeedbackAction.valueOf(rs.getString("action")),
            rs.getString("note"), rs.getString("ticket_url"), rs.getTimestamp("created_at").toInstant(),
        ) }, id,
    )

    override fun award(feedback: Feedback): UUID {
        val id = UUID.randomUUID()
        val inserted = jdbc.update(
            """INSERT INTO activity_credits
                (id, participant_id, season_id, credit_type, uniqueness_key, source_reference, points, activity_at,
                    source_name, source_occurred_at)
                SELECT ?, ?, ?, 'FEEDBACK', ?, ?, points, ?, 'Qualifying feedback', ?
                FROM program_activity_points WHERE credit_type = 'FEEDBACK'""",
            id, feedback.submitterId, feedback.seasonId, "feedback:${feedback.id}", "feedback:${feedback.id}",
            Timestamp.from(feedback.createdAt), Timestamp.from(feedback.createdAt),
        )
        check(inserted == 1) { "Feedback scoring configuration is missing" }
        return id
    }

    override fun update(feedback: Feedback, request: FeedbackActionRequest, actor: String, at: Instant) {
        val updated = jdbc.update(
            """UPDATE program_feedback SET status = ?, owner_email = ?, score_decision = ?, credit_id = ?,
                version = version + 1 WHERE id = ? AND version = ?""",
            feedback.status.name, feedback.ownerEmail, feedback.scoreDecision.name, feedback.creditId, feedback.id, request.expectedVersion,
        )
        check(updated == 1) { "Locked feedback changed unexpectedly" }
        jdbc.update(
            """INSERT INTO program_feedback_history(feedback_id, actor_email, action, note, ticket_url, created_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
            feedback.id, actor, request.action.name, request.note, request.ticketUrl, Timestamp.from(at),
        )
    }
}
