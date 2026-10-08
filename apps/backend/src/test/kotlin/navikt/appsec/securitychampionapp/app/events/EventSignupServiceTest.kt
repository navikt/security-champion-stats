package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.participation.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class EventSignupServiceTest {
    private val source = mock<DeltaSignupSource>()
    private val participants = mock<ParticipantStore>()
    private val clock = mock<Clock>()
    private val now = Instant.parse("2026-10-08T08:00:00Z")
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val event = Event(
        id.toString(), "Meetup", "", "2026-10-20T08:00:00Z", "2026-10-20T09:00:00Z", "", "meetup",
    )
    private val participant = ProgramParticipant(
        UUID.randomUUID(), "participant@nav.no", null, "participant@nav.no", "Participant",
        emptyList(), ParticipationStatus.ACTIVE, "2026-01-01",
    )

    init {
        whenever(clock.instant()).thenReturn(now)
        whenever(clock.zone).thenReturn(ZoneOffset.UTC)
        whenever(clock.withZone(any())).thenReturn(Clock.fixed(now, ZoneOffset.UTC))
        whenever(participants.findByNavNoEmail(any())).thenReturn(participant)
    }

    private fun service() = EventSignupService(source, participants, clock)
    private fun roster(emails: Set<String>, hosts: Set<String> = emptySet()) =
        DeltaSignupRoster(id, Instant.parse(event.startDate), emails, hosts)

    @Test
    fun `signup is personalized and hosts are separate from registration`() {
        whenever(source.signupRoster(id)).thenReturn(roster(setOf("participant@nav.no"), setOf("host@nav.no")))
        val service = service()
        val signedUp = service.forParticipant(listOf(event), " PARTICIPANT@nav.no ").single()
        assertThat(signedUp.signupStatus).isEqualTo(EventSignupStatus.SIGNED_UP)
        assertThat(signedUp.signupCheckedAt).isEqualTo(now.toString())
        assertThat(service.forParticipant(listOf(event), "host@nav.no").single().signupStatus).isEqualTo(EventSignupStatus.HOST)
        assertThat(service.forParticipant(listOf(event), "other@nav.no").single().signupStatus).isEqualTo(EventSignupStatus.NOT_SIGNED_UP)
        verify(source, times(1)).signupRoster(id)
    }

    @Test
    fun `cache expires after sixty seconds`() {
        whenever(source.signupRoster(id)).thenReturn(roster(emptySet()), roster(setOf("participant@nav.no")))
        val service = service()
        assertThat(service.forParticipant(listOf(event), participant.navNoEmail).single().signupStatus)
            .isEqualTo(EventSignupStatus.NOT_SIGNED_UP)
        whenever(clock.instant()).thenReturn(now.plusSeconds(60))
        assertThat(service.forParticipant(listOf(event), participant.navNoEmail).single().signupStatus)
            .isEqualTo(EventSignupStatus.SIGNED_UP)
        verify(source, times(2)).signupRoster(id)
    }

    @Test
    fun `Delta failures are explicitly unavailable and leave other catalog events intact`() {
        whenever(source.signupRoster(id)).thenThrow(EventSignupUnavailableException())
        val manual = event.copy(id = "manual", deltaEvent = false)
        val result = service().forParticipant(listOf(event, manual), participant.navNoEmail)
        assertThat(result[0].signupStatus).isEqualTo(EventSignupStatus.UNAVAILABLE)
        assertThat(result[0].signupCheckedAt).isNull()
        assertThat(result[1]).isEqualTo(manual)
    }

    @Test
    fun `inactive viewers and non Delta or past events do not fetch private registration data`() {
        whenever(participants.findByNavNoEmail(any())).thenReturn(participant.copy(status = ParticipationStatus.LEFT))
        assertThat(service().forParticipant(listOf(event), participant.navNoEmail).single().signupStatus).isNull()
        whenever(participants.findByNavNoEmail(any())).thenReturn(participant)
        val past = event.copy(startDate = "2026-01-01T08:00:00Z", endDate = "2026-01-01T09:00:00Z")
        val nonDelta = event.copy(deltaEvent = false)
        val result = service().forParticipant(listOf(past, nonDelta), participant.navNoEmail)
        assertThat(result.map { it.signupStatus }).containsOnlyNulls()
        verifyNoInteractions(source)
    }

    @Test
    fun `Delta linked feed events are supported without trusting arbitrary hosts`() {
        val feed = event.copy(id = "external:delta", deltaEvent = false, allDay = true,
            startDate = "2026-10-20", endDate = "2026-10-20", link = "https://delta.nav.no/event/$id")
        whenever(source.signupRoster(id)).thenReturn(roster(emptySet()))
        assertThat(service().forParticipant(listOf(feed), participant.navNoEmail).single().signupSupported).isTrue()
        assertThat(deltaSignupEventId(feed.copy(link = "https://delta.nav.no.invalid/event/$id"))).isNull()
        assertThat(deltaSignupEventId(feed.copy(link = "https://example.org/event/$id"))).isNull()
    }
}
