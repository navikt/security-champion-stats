package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.integrations.postgress.dto.EventQueryResponse
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
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
    private fun queryEvents(query: String, vararg args: Any): EventQueryResponse {
        return try {
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
                    amountOfPeopleJoined = rs.getInt("amount_of_people_joined")
                )
            }

            if (args.isEmpty()) {
                EventQueryResponse(
                    isOk = true,
                    jdbcTemplate.query(query, rowMapper)
                )
            } else {
                EventQueryResponse(
                    isOk = true,
                    jdbcTemplate.query(query, rowMapper, *args)
                )
            }
        } catch (e: DataAccessException) {
            EventQueryResponse(
                isOk = false,
                emptyList(),
                error = e.message
            )
        }
    }

    private fun insertEvents(query: String, vararg args: Any): EventQueryResponse {
        try {
            jdbcTemplate.update(query, *args)
            return EventQueryResponse(
                isOk = true
            )
        } catch (e: DuplicateKeyException) {
            throw e
        } catch (e: DataAccessException) {
            return EventQueryResponse(
                isOk = false,
                error = e.message
            )
        }
    }

    fun getAllEvents(): EventQueryResponse {
        val query = "SELECT " +
                "id, name, description, start_date, end_date, external_event, delta_event, location, " +
                "event_type, amount_of_people_joined FROM Events"
        return queryEvents(query)
    }

    fun addEvent(event: Event): EventQueryResponse {
        val query = "INSERT INTO Events (id, name, description, start_date, end_date, external_event, " +
                "delta_event, location, event_type, amount_of_people_joined) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        return insertEvents(
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
}