package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventDetails
import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventSource
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime
import java.util.UUID

class DeltaEventImportServiceTest {
    private val eventSource = mock<DeltaEventSource>()
    private val eventRepository = mock<EventRepository>()
    private val service = DeltaEventImportService(eventSource, eventRepository, 54, "https://delta.nav.no/")

    @Test
    fun `should save Delta events from the configured category with a link to Delta`() {
        whenever(eventSource.eventsInCategory(54)).thenReturn(listOf(deltaEvent()))

        val summary = service.import()

        val captor = argumentCaptor<Event>()
        verify(eventRepository).upsertDeltaEvent(captor.capture())
        assertThat(captor.firstValue).isEqualTo(
            Event(
                id = EVENT_ID.toString(),
                name = "Security meetup",
                description = "Synthetic description",
                startDate = "2026-10-03T08:00:00Z",
                endDate = "2026-10-03T09:00:00Z",
                location = "Oslo",
                type = "meetup",
                externalEvent = false,
                deltaEvent = true,
                link = "https://delta.nav.no/event/$EVENT_ID",
            )
        )
        assertThat(summary).isEqualTo(DeltaEventImportSummary(1, 1, 0))
    }

    @Test
    fun `should truncate name and location to fit the event table`() {
        whenever(eventSource.eventsInCategory(54))
            .thenReturn(listOf(deltaEvent().copy(title = "t".repeat(150), location = "l".repeat(150))))

        service.import()

        val captor = argumentCaptor<Event>()
        verify(eventRepository).upsertDeltaEvent(captor.capture())
        assertThat(captor.firstValue.name).hasSize(100)
        assertThat(captor.firstValue.location).hasSize(100)
    }

    @Test
    fun `should continue importing when an event conflicts with an existing program event`() {
        val conflicting = deltaEvent()
        val other = deltaEvent().copy(id = UUID.randomUUID(), title = "Other")
        whenever(eventSource.eventsInCategory(54)).thenReturn(listOf(conflicting, other))
        doThrow(DuplicateKeyException("duplicate")).doNothing().whenever(eventRepository).upsertDeltaEvent(any())

        val summary = service.import()

        verify(eventRepository, times(2)).upsertDeltaEvent(any())
        assertThat(summary).isEqualTo(DeltaEventImportSummary(2, 1, 1))
    }

    private fun deltaEvent() = DeltaEventDetails(
        id = EVENT_ID,
        title = "Security meetup",
        description = "Synthetic description",
        startTime = LocalDateTime.parse("2026-10-03T10:00:00"),
        endTime = LocalDateTime.parse("2026-10-03T11:00:00"),
        location = "Oslo",
    )

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    }
}
