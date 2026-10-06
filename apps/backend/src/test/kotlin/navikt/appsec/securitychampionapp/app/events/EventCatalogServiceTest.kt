package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.integrations.playbook.PlaybookEvent
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PlaybookEventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.EventQueryResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException

class EventCatalogServiceTest {
    private val ownRepository = mock<EventRepository>()
    private val playbookRepository = mock<PlaybookEventRepository>()
    private val service = EventCatalogService(ownRepository, playbookRepository)

    @Test
    fun `should prioritize Delta and manual events on the same Oslo start date in past and future`() {
        val ownEvents = listOf(
            ownEvent("delta", "2022-01-20T23:30:00Z"),
            ownEvent("manual", "2026-10-20T22:30:00Z").copy(deltaEvent = false),
        )
        whenever(ownRepository.getAllEvents()).thenReturn(EventQueryResponse(true, ownEvents))
        whenever(playbookRepository.findAll()).thenReturn(
            listOf(
                feedEvent("playbook:past", "2022-01-21"),
                feedEvent("playbook:future", "2026-10-21"),
                feedEvent("external:conference", "2026-10-21"),
                feedEvent("playbook:other", "2026-10-22"),
            )
        )

        val result = service.getAllEvents()

        assertThat(result.isOk).isTrue()
        assertThat(result.queryResult!!.map { it.id })
            .containsExactly("delta", "manual", "external:conference", "playbook:other")
        assertThat(result.queryResult.take(2)).isEqualTo(ownEvents)
        val conference = result.queryResult[2]
        assertThat(conference.allDay).isTrue()
        assertThat(conference.deltaEvent).isFalse()
        assertThat(conference.externalEvent).isTrue()
        assertThat(conference.startDate).isEqualTo("2026-10-21")
    }

    @Test
    fun `should restore a suppressed playbook event after an own event is rescheduled or removed`() {
        whenever(playbookRepository.findAll()).thenReturn(listOf(feedEvent("playbook:meetup", "2026-10-21")))
        whenever(ownRepository.getAllEvents()).thenReturn(
            EventQueryResponse(true, listOf(ownEvent("manual", "2026-10-21T12:00:00Z"))),
            EventQueryResponse(true, listOf(ownEvent("manual", "2026-10-22T12:00:00Z"))),
            EventQueryResponse(true, emptyList()),
        )

        assertThat(service.getAllEvents().queryResult!!.map { it.id }).containsExactly("manual")
        assertThat(service.getAllEvents().queryResult!!.map { it.id }).containsExactly("manual", "playbook:meetup")
        assertThat(service.getAllEvents().queryResult!!.map { it.id }).containsExactly("playbook:meetup")
    }

    @Test
    fun `should compare start dates rather than suppressing overlapping multi day events`() {
        whenever(ownRepository.getAllEvents())
            .thenReturn(EventQueryResponse(true, listOf(ownEvent("manual", "2026-10-21T12:00:00Z"))))
        whenever(playbookRepository.findAll()).thenReturn(
            listOf(feedEvent("playbook:course", "2026-10-20").copy(endDate = "2026-10-22"))
        )

        assertThat(service.getAllEvents().queryResult!!.map { it.id }).containsExactly("manual", "playbook:course")
    }

    @Test
    fun `should suppress exact Delta UUID matches regardless of feed prefix or corrected dates`() {
        val deltaId = "ad4380ae-7f69-4fdd-a8c1-6796293e4be5"
        val matching = feedEvent("external:delta", "2024-02-01").copy(
            url = "https://delta.nav.no/event/${deltaId.uppercase()}/?source=playbook",
        )
        val unrelated = matching.copy(
            id = "external:unrelated", url = "https://example.org/event/$deltaId",
        )
        whenever(playbookRepository.findAll()).thenReturn(listOf(matching, unrelated))
        whenever(ownRepository.getAllEvents()).thenReturn(
            EventQueryResponse(true, listOf(ownEvent(deltaId, "2024-02-02T12:00:00Z"))),
            EventQueryResponse(true, emptyList()),
        )

        assertThat(service.getAllEvents().queryResult!!.map { it.id })
            .containsExactly(deltaId, "external:unrelated")
        assertThat(service.getAllEvents().queryResult!!.map { it.id })
            .containsExactly("external:delta", "external:unrelated")
    }

    @Test
    fun `should report storage errors instead of returning a partial catalog`() {
        whenever(ownRepository.getAllEvents()).thenReturn(EventQueryResponse(false, error = "unavailable"))
        assertThat(service.getAllEvents().isOk).isFalse()
        verifyNoInteractions(playbookRepository)

        whenever(ownRepository.getAllEvents()).thenReturn(EventQueryResponse(true, emptyList()))
        whenever(playbookRepository.findAll()).thenThrow(DataAccessResourceFailureException("unavailable"))
        assertThat(service.getAllEvents().isOk).isFalse()
    }

    private fun ownEvent(id: String, start: String) = Event(
        id = id, name = "Own event", description = "", startDate = start, endDate = start,
        location = "Oslo", type = "meetup",
    )

    private fun feedEvent(id: String, start: String) = PlaybookEvent(
        id = id, title = "Playbook event", startDate = start, endDate = start,
        audience = "Alle", url = "https://sikkerhet.nav.no/docs/events/test",
    )
}
