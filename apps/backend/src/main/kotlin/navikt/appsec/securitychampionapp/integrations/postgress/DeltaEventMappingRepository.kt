package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMapping
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMappingHasCreditsException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class DeltaEventMappingRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val mappingMapper = RowMapper { rs, _ ->
        DeltaEventMapping(
            id = rs.getObject("id", UUID::class.java),
            programEventName = rs.getString("program_event_name"),
            deltaEventUuid = rs.getObject("delta_event_uuid", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    fun findAll(): List<DeltaEventMapping> =
        jdbcTemplate.query(
            """
                SELECT id, program_event_name, delta_event_uuid, created_at
                FROM program_delta_event_mappings
                ORDER BY LOWER(program_event_name), id
            """.trimIndent(),
            mappingMapper,
        )

    @Transactional
    fun addMapping(
        id: UUID,
        programEventName: String,
        deltaEventUuid: UUID,
        actorNavNoEmail: String,
    ): DeltaEventMapping {
        val mapping = jdbcTemplate.queryForObject(
            """
                INSERT INTO program_delta_event_mappings (
                    id, program_event_name, delta_event_uuid, created_by_nav_no_email
                ) VALUES (?, ?, ?, ?)
                RETURNING id, program_event_name, delta_event_uuid, created_at
            """.trimIndent(),
            mappingMapper,
            id,
            programEventName,
            deltaEventUuid,
            actorNavNoEmail,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    actor_nav_no_email, action, affected_record_id, before_values, after_values
                ) VALUES (?, 'DELTA_EVENT_MAPPING_ADDED', ?, '{}'::jsonb, jsonb_build_object(
                    'programEventName', ?, 'deltaEventUuid', ?
                ))
            """.trimIndent(),
            actorNavNoEmail,
            id,
            programEventName,
            deltaEventUuid.toString(),
        )
        return mapping
    }

    @Transactional
    fun removeMapping(id: UUID, actorNavNoEmail: String): Boolean {
        val mapping = jdbcTemplate.query(
            """
                SELECT id, program_event_name, delta_event_uuid, created_at
                FROM program_delta_event_mappings
                WHERE id = ?
                FOR UPDATE
            """.trimIndent(),
            mappingMapper,
            id,
        ).firstOrNull() ?: return false

        val hasCredits = jdbcTemplate.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1 FROM activity_credits
                    WHERE credit_type = 'DELTA_REGISTRATION'
                    AND source_reference = ?
                )
            """.trimIndent(),
            Boolean::class.javaObjectType,
            mapping.deltaEventUuid.toString(),
        ) ?: false
        if (hasCredits) throw DeltaEventMappingHasCreditsException()

        jdbcTemplate.update("DELETE FROM program_delta_event_mappings WHERE id = ?", id)
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    actor_nav_no_email, action, affected_record_id, before_values, after_values
                ) VALUES (?, 'DELTA_EVENT_MAPPING_REMOVED', ?, jsonb_build_object(
                    'programEventName', ?, 'deltaEventUuid', ?
                ), '{}'::jsonb)
            """.trimIndent(),
            actorNavNoEmail,
            id,
            mapping.programEventName,
            mapping.deltaEventUuid.toString(),
        )
        return true
    }
}
