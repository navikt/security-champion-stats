package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.api.dto.Event
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class EventRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    private fun queryEvents(query: String, vararg args: Any): List<Event> {
        val rowMapper = RowMapper { rs, _ ->
            Event(
                id = rs.getString("id"),
                name = rs.getString("name"),
                description = rs.getString("description"),
                startDate = rs.getTimestamp("start_date").toInstant().toString(),
                endDate = rs.getTimestamp("end_date").toInstant().toString(),
                externalEvent = rs.getBoolean("external_event"),
                deltaEvent = rs.getBoolean("delta_event"),
                location = rs.getString("location"),
                type = rs.getString("event_type").lowercase(),
                amountOfPeopleJoined = rs.getInt("amount_of_people_joined"),
                link = rs.getString("link"),
            )
        }
        return if (args.isEmpty()) {
            jdbcTemplate.query(query, rowMapper)
        } else {
            jdbcTemplate.query(query, rowMapper, *args)
        }
    }

    fun getAllEvents(): List<Event> {
        val query = "SELECT " +
                "id, name, description, start_date, end_date, external_event, delta_event, location, " +
                "event_type, amount_of_people_joined, link FROM Events"
        return queryEvents(query)
    }

    fun addEvent(event: Event) {
        val query = "INSERT INTO Events (id, name, description, start_date, end_date, external_event, " +
                "delta_event, location, event_type, amount_of_people_joined) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        jdbcTemplate.update(
            query,
            UUID.fromString(event.id),
            event.name,
            event.description,
            Timestamp.from(Instant.parse(event.startDate)),
            Timestamp.from(Instant.parse(event.endDate)),
            event.externalEvent,
            event.deltaEvent,
            event.location,
            event.type.uppercase(),
            0
        )
    }

    fun upsertDeltaEvent(event: Event) {
        val query = """
            INSERT INTO Events (id, name, description, start_date, end_date, external_event,
                delta_event, location, event_type, amount_of_people_joined, link)
            VALUES (?, ?, ?, ?, ?, FALSE, TRUE, ?, ?, 0, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                start_date = EXCLUDED.start_date,
                end_date = EXCLUDED.end_date,
                location = EXCLUDED.location,
                link = EXCLUDED.link
        """.trimIndent()
        jdbcTemplate.update(
            query,
            UUID.fromString(event.id),
            event.name,
            event.description,
            Timestamp.from(Instant.parse(event.startDate)),
            Timestamp.from(Instant.parse(event.endDate)),
            event.location,
            event.type.uppercase(),
            event.link,
        )
    }
}