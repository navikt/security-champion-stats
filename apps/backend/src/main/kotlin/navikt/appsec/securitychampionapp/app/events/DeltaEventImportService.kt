package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventDetails
import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventSource
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

private const val MAX_TEXT_LENGTH = 100
private val DELTA_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

data class DeltaEventImportSummary(
    val eventsFetched: Int,
    val eventsSaved: Int,
    val conflicts: Int,
)

@Service
class DeltaEventImportService(
    private val eventSource: DeltaEventSource,
    private val eventRepository: EventRepository,
    @Value($$"${delta.events.category-id:54}") private val categoryId: Int,
    @Value($$"${delta.events.link-base-url:https://delta.nav.no}") private val linkBaseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(DeltaEventImportService::class.java)

    fun import(): DeltaEventImportSummary {
        val events = eventSource.eventsInCategory(categoryId)
        var saved = 0
        var conflicts = 0
        events.forEach { deltaEvent ->
            try {
                eventRepository.upsertDeltaEvent(deltaEvent.toEvent())
                saved++
            } catch (_: DuplicateKeyException) {
                logger.warn("Skipped Delta event {} because a matching program event already exists", deltaEvent.id)
                conflicts++
            }
        }
        return DeltaEventImportSummary(events.size, saved, conflicts)
    }

    private fun DeltaEventDetails.toEvent() = Event(
        id = id.toString(),
        name = title.take(MAX_TEXT_LENGTH),
        description = description,
        startDate = startTime.toInstantString(),
        endDate = endTime.toInstantString(),
        location = location.take(MAX_TEXT_LENGTH),
        type = "meetup",
        externalEvent = false,
        deltaEvent = true,
        link = "${linkBaseUrl.trimEnd('/')}/event/$id",
    )

    private fun LocalDateTime.toInstantString() = atZone(DELTA_ZONE).toInstant().toString()
}
