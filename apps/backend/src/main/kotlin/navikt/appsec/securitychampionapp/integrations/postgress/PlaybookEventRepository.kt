package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.integrations.playbook.PlaybookEvent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date

@Repository
class PlaybookEventRepository(private val jdbcTemplate: JdbcTemplate) {
    fun findAll(): List<PlaybookEvent> =
        jdbcTemplate.query("SELECT id, title, start_date, end_date, audience, url FROM playbook_events") { rs, _ ->
            PlaybookEvent(
                id = rs.getString("id"),
                title = rs.getString("title"),
                startDate = rs.getDate("start_date").toLocalDate().toString(),
                endDate = rs.getDate("end_date").toLocalDate().toString(),
                audience = rs.getString("audience"),
                url = rs.getString("url"),
            )
        }

    @Transactional
    fun replaceSnapshot(events: List<PlaybookEvent>) {
        jdbcTemplate.update("DELETE FROM playbook_events")
        events.forEach { event ->
            jdbcTemplate.update(
                "INSERT INTO playbook_events (id, title, start_date, end_date, audience, url) VALUES (?, ?, ?, ?, ?, ?)",
                event.id,
                event.title,
                Date.valueOf(event.startDate),
                Date.valueOf(event.endDate),
                event.audience,
                event.url,
            )
        }
    }
}
