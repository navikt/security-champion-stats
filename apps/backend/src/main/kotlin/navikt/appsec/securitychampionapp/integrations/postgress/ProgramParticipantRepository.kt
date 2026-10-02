package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantQueryResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantUpdateResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlTextArray
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ProgramParticipantRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val rowMapper = RowMapper { rs, _ ->
        val teams = (rs.getArray("teams")?.array as? Array<*>)
            ?.mapNotNull { team -> team?.toString() }
            ?: emptyList()
        ProgramParticipant(
            id = rs.getString("id"),
            navNoEmail = rs.getString("nav_no_email"),
            navIdent = rs.getString("nav_ident"),
            email = rs.getString("email"),
            fullname = rs.getString("fullname"),
            teams = teams,
            status = rs.getString("status"),
            createdAt = rs.getString("created_at"),
        )
    }

    fun findByNavNoEmail(navNoEmail: String): ProgramParticipantQueryResponse =
        query(
            "SELECT * FROM program_participants WHERE nav_no_email = ?",
            navNoEmail,
        )

    fun findActiveParticipants(): ProgramParticipantQueryResponse =
        query(
            "SELECT * FROM program_participants WHERE status = 'ACTIVE' ORDER BY fullname",
        )

    fun findAllParticipants(): ProgramParticipantQueryResponse =
        query("SELECT * FROM program_participants ORDER BY fullname")

    fun enroll(
        navNoEmail: String,
        navIdent: String,
        email: String,
        fullname: String = "",
        teams: List<String> = emptyList(),
    ): ProgramParticipantUpdateResponse = update(
        """
            INSERT INTO program_participants (id, nav_no_email, nav_ident, email, fullname, teams)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (nav_no_email) DO NOTHING
        """.trimIndent(),
        UUID.randomUUID(),
        navNoEmail,
        navIdent,
        email,
        fullname,
        SqlTextArray(teams),
    )

    fun updateAuthenticatedIdentity(
        navNoEmail: String,
        navIdent: String,
        email: String,
    ): ProgramParticipantUpdateResponse = update(
        """
            UPDATE program_participants
            SET nav_ident = ?, email = ?, updated_at = NOW()
            WHERE nav_no_email = ?
        """.trimIndent(),
        navIdent,
        email,
        navNoEmail,
    )

    fun updateProfile(
        navIdent: String,
        email: String,
        fullname: String,
        teams: List<String>,
    ): ProgramParticipantUpdateResponse = update(
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

    fun updateStatus(
        id: UUID,
        active: Boolean,
        actorNavNoEmail: String,
    ): ProgramParticipantUpdateResponse = update(
        """
            WITH target AS (
                SELECT id, status
                FROM program_participants
                WHERE id = ?
                FOR UPDATE
            ),
            changed AS (
                UPDATE program_participants AS participant
                SET status = ?, updated_at = NOW()
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

    fun permanentlyDelete(
        id: UUID,
    ): ProgramParticipantUpdateResponse = update(
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
            deleted_legacy_members AS (
                DELETE FROM Members
                WHERE email IN (SELECT email FROM target)
                RETURNING id
            )
            DELETE FROM program_participants
            WHERE id = ?
        """.trimIndent(),
        id,
        id,
    )

    private fun query(query: String, vararg args: Any): ProgramParticipantQueryResponse =
        try {
            val participants = if (args.isEmpty()) {
                jdbcTemplate.query(query, rowMapper)
            } else {
                jdbcTemplate.query(query, rowMapper, *args)
            }
            ProgramParticipantQueryResponse(isOk = true, queryResult = participants)
        } catch (e: Exception) {
            ProgramParticipantQueryResponse(
                isOk = false,
                queryResult = emptyList(),
                error = "Failed to fetch program participants: ${e.message}",
            )
        }

    private fun update(query: String, vararg args: Any): ProgramParticipantUpdateResponse =
        try {
            val affectedRows = jdbcTemplate.update { connection ->
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
            ProgramParticipantUpdateResponse(isOk = true, affectedRows = affectedRows)
        } catch (e: Exception) {
            ProgramParticipantUpdateResponse(
                isOk = false,
                error = "Failed to update program participant: ${e.message}",
            )
        }
}
