package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PlaybookEventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.EventQueryResponse
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.time.ZoneId

@Service
class EventCatalogService(
    private val eventRepository: EventRepository,
    private val playbookRepository: PlaybookEventRepository,
) {
    fun getAllEvents(): EventQueryResponse {
        val ownEvents = eventRepository.getAllEvents()
        if (!ownEvents.isOk) return ownEvents
        val events = requireNotNull(ownEvents.queryResult)
        val ownDates = events.map { Instant.parse(it.startDate).atZone(OSLO_ZONE).toLocalDate().toString() }.toSet()
        val ownIds = events.map { it.id.lowercase() }.toSet()
        return try {
            val playbookEvents = playbookRepository.findAll()
                .filterNot {
                    (it.id.startsWith("playbook:") && it.startDate in ownDates) ||
                        deltaEventId(it.url) in ownIds
                }
                .map {
                    Event(
                        id = it.id,
                        name = it.title,
                        description = it.audience,
                        startDate = it.startDate,
                        endDate = it.endDate,
                        location = "",
                        type = "event",
                        externalEvent = it.id.startsWith("external:"),
                        deltaEvent = false,
                        link = it.url,
                        allDay = true,
                    )
                }
            EventQueryResponse(isOk = true, queryResult = events + playbookEvents)
        } catch (e: DataAccessException) {
            EventQueryResponse(isOk = false, error = e.message)
        }
    }

    private fun deltaEventId(url: String): String? {
        val uri = URI(url)
        if (!uri.host.equals("delta.nav.no", ignoreCase = true)) return null
        return DELTA_EVENT_PATH.matchEntire(uri.path)?.groupValues?.get(1)?.lowercase()
    }

    private companion object {
        val OSLO_ZONE: ZoneId = ZoneId.of("Europe/Oslo")
        val DELTA_EVENT_PATH = Regex(
            "/event/([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})/?"
        )
    }
}
