package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.participation.DeactivationReason
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlTextArray
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ProgramParticipantRepository(
    private val jdbcTemplate: JdbcTemplate,
) : ParticipantStore {
    private val rowMapper = RowMapper { rs, _ ->
        val teams = (rs.getArray("teams")?.array as? Array<*>)
            ?.mapNotNull { team -> team?.toString() }
            ?: emptyList()
        ProgramParticipant(
            id = rs.getObject("id", UUID::class.java),
            navNoEmail = rs.getString("nav_no_email"),
            navIdent = rs.getString("nav_ident"),
            email = rs.getString("email"),
            fullname = rs.getString("fullname"),
            teams = teams,
            status = ParticipationStatus.valueOf(rs.getString("status")),
            createdAt = rs.getString("created_at"),
            deactivationReason = rs.getString("deactivation_reason")?.let(DeactivationReason::valueOf),
        )
    }

    override fun findById(id: UUID): ProgramParticipant? =
        query("SELECT * FROM program_participants WHERE id = ?", id).singleOrNull()

    override fun findByNavNoEmail(navNoEmail: String): ProgramParticipant? =
        query("SELECT * FROM program_participants WHERE nav_no_email = ?", navNoEmail).singleOrNull()

    override fun findActiveParticipants(): List<ProgramParticipant> =
        query("SELECT * FROM program_participants WHERE status = 'ACTIVE' ORDER BY fullname")

    override fun findAllParticipants(): List<ProgramParticipant> =
        query("SELECT * FROM program_participants ORDER BY fullname")

    override fun enroll(
        id: UUID,
        navNoEmail: String,
        navIdent: String,
        email: String,
        fullname: String,
        teams: List<String>,
    ): Int = update(
        """
            INSERT INTO program_participants (id, nav_no_email, nav_ident, email, fullname, teams)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (nav_no_email) DO NOTHING
        """.trimIndent(),
        id,
        navNoEmail,
        navIdent,
        email,
        fullname,
        SqlTextArray(teams),
    )

    fun enroll(
        navNoEmail: String,
        navIdent: String,
        email: String,
        fullname: String = "",
        teams: List<String> = emptyList(),
    ): Int = enroll(UUID.randomUUID(), navNoEmail, navIdent, email, fullname, teams)

    override fun updateAuthenticatedIdentity(
        navNoEmail: String,
        navIdent: String,
        email: String,
    ): Int = update(
        """
            UPDATE program_participants
            SET nav_ident = ?, email = ?, updated_at = NOW()
            WHERE nav_no_email = ?
        """.trimIndent(),
        navIdent,
        email,
        navNoEmail,
    )

    override fun leave(navNoEmail: String): Int = update(
        """
            UPDATE program_participants
            SET status = 'LEFT', updated_at = NOW()
            WHERE nav_no_email = ? AND status = 'ACTIVE'
        """.trimIndent(),
        navNoEmail,
    )

    override fun rejoin(navNoEmail: String): Int = update(
        """
            UPDATE program_participants
            SET status = 'ACTIVE', updated_at = NOW()
            WHERE nav_no_email = ? AND status = 'LEFT'
        """.trimIndent(),
        navNoEmail,
    )

    override fun updateProfile(
        navIdent: String,
        email: String,
        fullname: String,
        teams: List<String>,
    ): Int = update(
        """
            UPDATE program_participants
            SET fullname = ?, teams = ?, updated_at = NOW()
            WHERE nav_ident = ? AND email = ?
        """.trimIndent(),
        fullname,
        SqlTextArray(teams),
        navIdent,
        email,
    )

    override fun updateStatus(
        id: UUID,
        active: Boolean,
        actorNavNoEmail: String,
    ): Int = update(
        """
            WITH target AS (
                SELECT id, status
                FROM program_participants
                WHERE id = ?
                FOR UPDATE
            ),
            changed AS (
                UPDATE program_participants AS participant
                SET status = ?, deactivation_reason = NULL, updated_at = NOW()
                FROM target
                WHERE participant.id = target.id
                RETURNING participant.id, participant.status
            )
            INSERT INTO program_participant_audit (
                participant_id, actor_nav_no_email, action, before_values, after_values
            )
            SELECT
                target.id,
                ?,
                'PARTICIPATION_STATUS_CHANGED',
                jsonb_build_object('status', target.status),
                jsonb_build_object('status', changed.status)
            FROM target
            JOIN changed USING (id)
        """.trimIndent(),
        id,
        if (active) "ACTIVE" else "DEACTIVATED",
        actorNavNoEmail,
    )

    override fun permanentlyDelete(id: UUID): Int = update(
        """
            WITH target AS MATERIALIZED (
                SELECT id, nav_no_email, email
                FROM program_participants
                WHERE id = ?
            ),
            anonymized_actor_history AS (
                UPDATE program_participant_audit AS audit
                SET actor_nav_no_email = NULL
                FROM target
                WHERE audit.actor_nav_no_email = target.nav_no_email
                    AND audit.participant_id <> target.id
                RETURNING audit.id
            ),
            anonymized_scoring_actor_history AS (
                UPDATE program_scoring_audit AS audit
                SET actor_nav_no_email = NULL
                FROM target
                WHERE audit.actor_nav_no_email = target.nav_no_email
                RETURNING audit.id
            ),
            anonymized_program_actor_history AS (
                UPDATE program_audit_events AS audit
                SET actor_nav_no_email = NULL
                FROM target
                WHERE audit.actor_nav_no_email = target.nav_no_email
                RETURNING audit.id
            ),
            deleted_legacy_members AS (
                DELETE FROM Members
                WHERE email IN (SELECT email FROM target)
                RETURNING id
            ),
            anonymized_adjustment_actors AS (
                UPDATE point_adjustments SET actor_nav_no_email = NULL
                WHERE actor_nav_no_email IN (SELECT nav_no_email FROM target)
                RETURNING id
            ),
            anonymized_event_review_actors AS (
                UPDATE event_claim_reviews SET actor_nav_no_email = NULL
                WHERE actor_nav_no_email IN (SELECT nav_no_email FROM target)
                RETURNING id
            ),
            anonymized_slack_creators AS (
                UPDATE slack_account_mappings SET created_by_nav_no_email = NULL
                WHERE created_by_nav_no_email IN (SELECT nav_no_email FROM target)
                RETURNING slack_user_id
            ),
            anonymized_delta_creators AS (
                UPDATE program_delta_event_mappings SET created_by_nav_no_email = NULL
                WHERE created_by_nav_no_email IN (SELECT nav_no_email FROM target)
                RETURNING id
            ),
            anonymized_category_creators AS (
                UPDATE delta_eligible_categories SET created_by_nav_no_email = NULL
                WHERE created_by_nav_no_email IN (SELECT nav_no_email FROM target)
                RETURNING category_id
            )
            DELETE FROM program_participants
            WHERE id = ?
        """.trimIndent(),
        id,
        id,
    )

    private fun query(query: String, vararg args: Any): List<ProgramParticipant> =
        if (args.isEmpty()) {
            jdbcTemplate.query(query, rowMapper)
        } else {
            jdbcTemplate.query(query, rowMapper, *args)
        }

    private fun update(query: String, vararg args: Any): Int =
        jdbcTemplate.update { connection ->
            connection.prepareStatement(query).apply {
                args.forEachIndexed { index, value ->
                    val parameterIndex = index + 1
                    when (value) {
                        is SqlTextArray -> setArray(
                            parameterIndex,
                            connection.createArrayOf("text", value.value.toTypedArray()),
                        )
                        else -> setObject(parameterIndex, value)
                    }
                }
            }
        }
}
