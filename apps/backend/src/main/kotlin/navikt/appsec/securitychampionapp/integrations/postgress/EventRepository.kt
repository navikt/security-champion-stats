package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.integrations.postgress.dto.EventQueryResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlEvent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class EventRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    private fun queryEvents(query: String, vararg args: Any): EventQueryResponse {
        return try {
            val rowMapper = RowMapper { rs, _ ->
                SqlEvent(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    startDateTime = rs.getTimestamp("start_date").toInstant(),
                    endDateTime = rs.getTimestamp("end_date").toInstant(),
                    externalEvent = rs.getBoolean("external_event"),
                    deltaEvent = rs.getBoolean("delta_event"),
                    location = rs.getString("location")
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
        } catch (e: Exception) {
            EventQueryResponse(
                isOk = false,
                emptyList(),
                error = "Failed to fetch events: ${e.message}"
            )
        }
    }

    fun getAllEvents(): EventQueryResponse {
        val query = "SELECT id, name, description, start_date, end_date, external_event, delta_event, location FROM Events"
        return queryEvents(query)
    }
}