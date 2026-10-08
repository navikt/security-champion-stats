package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.participation.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class EventReminderServiceTest {
    private val catalog = mock<EventCatalogService>()
    private val source = mock<DeltaSignupSource>()
    private val participants = mock<ParticipantStore>()
    private val directory = mock<SlackParticipantDirectory>()
    private val store = mock<EventReminderStore>()
    private val gateway = mock<EventReminderGateway>()
    private val now = Instant.parse("2026-10-08T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val event = Event(
        id.toString(), "Security <meetup>", "", "2026-10-20T08:00:00Z", "2026-10-20T09:00:00Z", "", "meetup",
    )
    private val first = participant("first")
    private val second = participant("second")
    private val service = EventReminderService(catalog, source, participants, directory, store, gateway, clock)

    init {
        whenever(catalog.getAllEvents()).thenReturn(listOf(event))
        whenever(source.signupRoster(id)).thenReturn(roster())
        whenever(participants.findActiveParticipants()).thenReturn(listOf(first, second))
        whenever(participants.findById(first.id)).thenReturn(first)
        whenever(participants.findById(second.id)).thenReturn(second)
        whenever(directory.resolve(any())).thenReturn(SlackIdentityResolution(mapOf(first.id to "U_FIRST", second.id to "U_SECOND"), emptySet()))
        whenever(store.deliveries(id)).thenReturn(emptyList())
        whenever(store.claim(any(), any(), any(), any())).thenReturn(true)
        whenever(gateway.remind(any(), any(), any())).thenReturn("123.456")
    }

    private fun participant(name: String) = ProgramParticipant(
        UUID.randomUUID(), "$name@nav.no", null, "$name@nav.no", name, emptyList(), ParticipationStatus.ACTIVE, "2026-01-01",
    )
    private fun roster(registered: Set<String> = emptySet(), hosts: Set<String> = emptySet()) =
        DeltaSignupRoster(id, Instant.parse(event.startDate), registered, hosts)

    @Test
    fun `registered participants and hosts are excluded and preview has no side effects`() {
        whenever(source.signupRoster(id)).thenReturn(roster(setOf(first.navNoEmail), setOf(second.navNoEmail)))
        val preview = service.preview(event.id)
        assertThat(preview.signedUpParticipants).isEqualTo(2)
        assertThat(preview.recipients).isEmpty()
        assertThat(preview.message).contains("Security &lt;meetup&gt;", "https://delta.nav.no/event/$id", "10:00 CEST")
        verifyNoInteractions(gateway, directory)
        verify(store, never()).claim(any(), any(), any(), any())
    }

    @Test
    fun `unresolved sent uncertain and delayed accounts remain visible but are not ready`() {
        whenever(directory.resolve(any())).thenReturn(SlackIdentityResolution(emptyMap(), setOf(first.id, second.id)))
        assertThat(service.preview(event.id).recipients.map { it.status }).containsOnly(ReminderRecipientStatus.UNRESOLVED)
        whenever(store.deliveries(id)).thenReturn(listOf(
            ReminderDelivery(first.id, ReminderDeliveryStatus.SENT, now),
            ReminderDelivery(second.id, ReminderDeliveryStatus.SENDING, now),
        ))
        assertThat(service.preview(event.id).recipients.map { it.status })
            .containsExactlyInAnyOrder(ReminderRecipientStatus.ALREADY_SENT, ReminderRecipientStatus.DELIVERY_UNCERTAIN)
        whenever(store.deliveries(id)).thenReturn(listOf(
            ReminderDelivery(first.id, ReminderDeliveryStatus.FAILED, now.plusSeconds(900)),
            ReminderDelivery(second.id, ReminderDeliveryStatus.UNCERTAIN, now),
        ))
        assertThat(service.preview(event.id).recipients.map { it.status })
            .containsExactlyInAnyOrder(ReminderRecipientStatus.RETRY_LATER, ReminderRecipientStatus.DELIVERY_UNCERTAIN)
    }

    @Test
    fun `send rechecks Delta and rejects a stale signup preview before any claim`() {
        val preview = service.preview(event.id)
        whenever(source.signupRoster(id)).thenReturn(roster(setOf(first.navNoEmail)))
        assertThatThrownBy { service.send(event.id, preview.version, "Edited reminder") }.isInstanceOf(EventReminderConflictException::class.java)
        verifyNoInteractions(gateway)
        verify(store, never()).claim(any(), any(), any(), any())
    }

    @Test
    fun `changed membership Slack identities event details and delivery outcomes invalidate preview`() {
        val version = service.preview(event.id).version
        whenever(participants.findActiveParticipants()).thenReturn(listOf(first))
        assertThatThrownBy { service.validate(event.id, version, "Edited reminder") }.isInstanceOf(EventReminderConflictException::class.java)
        whenever(participants.findActiveParticipants()).thenReturn(listOf(first, second))
        whenever(directory.resolve(any())).thenReturn(SlackIdentityResolution(mapOf(first.id to "U_CHANGED", second.id to "U_SECOND"), emptySet()))
        assertThatThrownBy { service.validate(event.id, version, "Edited reminder") }.isInstanceOf(EventReminderConflictException::class.java)
        whenever(directory.resolve(any())).thenReturn(SlackIdentityResolution(mapOf(first.id to "U_FIRST", second.id to "U_SECOND"), emptySet()))
        whenever(catalog.getAllEvents()).thenReturn(listOf(event.copy(name = "Changed")))
        assertThatThrownBy { service.validate(event.id, version, "Edited reminder") }.isInstanceOf(EventReminderConflictException::class.java)
        whenever(catalog.getAllEvents()).thenReturn(listOf(event))
        whenever(store.deliveries(id)).thenReturn(listOf(ReminderDelivery(first.id, ReminderDeliveryStatus.SENT, now)))
        assertThatThrownBy { service.validate(event.id, version, "Edited reminder") }.isInstanceOf(EventReminderConflictException::class.java)
    }

    @Test
    fun `an unavailable roster never becomes an empty registration list or sends reminders`() {
        whenever(source.signupRoster(id)).thenThrow(EventSignupUnavailableException())
        assertThatThrownBy { service.preview(event.id) }.isInstanceOf(EventSignupUnavailableException::class.java)
        assertThatThrownBy { service.send(event.id, "old", "Edited reminder") }.isInstanceOf(EventSignupUnavailableException::class.java)
        verifyNoInteractions(directory, store, gateway)
    }

    @Test
    fun `a preview with no ready recipients cannot start a send`() {
        whenever(source.signupRoster(id)).thenReturn(roster(setOf(first.navNoEmail, second.navNoEmail)))
        val preview = service.preview(event.id)
        assertThatThrownBy { service.send(event.id, preview.version, preview.message) }
            .isInstanceOf(EventReminderConflictException::class.java).hasMessageContaining("No recipients")
        verifyNoInteractions(gateway)
        verify(store, never()).claim(any(), any(), any(), any())
    }

    @Test
    fun `unexpected persistence failures are propagated rather than becoming delivery success`() {
        doThrow(org.springframework.dao.DataAccessResourceFailureException("Database unavailable"))
            .whenever(store).finish(any(), any(), anyOrNull(), any())
        assertThatThrownBy { service.send(event.id, service.preview(event.id).version, "Edited reminder") }
            .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException::class.java)
    }

    @Test
    fun `started Delta events and non Delta events block reminders`() {
        whenever(source.signupRoster(id)).thenReturn(roster().copy(startsAt = now))
        assertThatThrownBy { service.preview(event.id) }.isInstanceOf(EventReminderConflictException::class.java)
        whenever(catalog.getAllEvents()).thenReturn(listOf(event.copy(deltaEvent = false)))
        assertThatThrownBy { service.preview(event.id) }.isInstanceOf(EventReminderConflictException::class.java)
        verifyNoInteractions(gateway)
    }

    @Test
    fun `each delivery is claimed before sending and a lost claim or inactive participant is skipped`() {
        val preview = service.preview(event.id)
        whenever(store.claim(eq(id), eq(first.id), any(), any())).thenReturn(false)
        whenever(participants.findById(second.id)).thenReturn(second.copy(status = ParticipationStatus.LEFT))
        assertThat(service.send(event.id, preview.version, preview.message)).isEqualTo(EventReminderResult(0, 0, 0, 2))
        verifyNoInteractions(gateway)
    }

    @Test
    fun `successful delivery persists timestamps before completing the batch`() {
        val result = service.send(event.id, service.preview(event.id).version, "Edited reminder")
        assertThat(result).isEqualTo(EventReminderResult(2, 0, 0, 0))
        inOrder(store, gateway) {
            verify(store).claim(eq(id), any(), any(), any())
            verify(gateway).remind(any(), any(), any())
            verify(store).finish(any(), eq(ReminderDeliveryStatus.SENT), eq("123.456"), eq(Duration.ZERO))
        }
        verify(store, times(2)).finish(any(), eq(ReminderDeliveryStatus.SENT), eq("123.456"), eq(Duration.ZERO))
    }

    @Test
    fun `uncertain Slack delivery is persisted and shared failures stop the batch`() {
        whenever(gateway.remind(any(), any(), any())).thenThrow(MembershipDeliveryException(true, "Network"))
        val result = service.send(event.id, service.preview(event.id).version, "Edited reminder")
        assertThat(result).isEqualTo(EventReminderResult(0, 0, 1, 1))
        verify(store).finish(any(), eq(ReminderDeliveryStatus.UNCERTAIN), isNull(), eq(Duration.ofMinutes(15)))
        verify(gateway, times(1)).remind(any(), any(), any())
    }

    @Test
    fun `known recipient rejections preserve retry delay and do not block other recipients`() {
        whenever(gateway.remind(any(), any(), any()))
            .thenThrow(MembershipDeliveryException(false, "Invalid user", stopBatch = false, retryAfter = Duration.ofSeconds(45)))
            .thenReturn("123.456")
        assertThat(service.send(event.id, service.preview(event.id).version, "Edited reminder")).isEqualTo(EventReminderResult(1, 1, 0, 0))
        verify(store).finish(any(), eq(ReminderDeliveryStatus.FAILED), isNull(), eq(Duration.ofSeconds(45)))
    }

    @Test
    fun `edited messages are delivered unchanged without resetting sent or uncertain recipients`() {
        whenever(store.deliveries(id)).thenReturn(listOf(ReminderDelivery(first.id, ReminderDeliveryStatus.SENT, now)))
        val preview = service.preview(event.id)
        val message = "Join us!\n<https://delta.nav.no/event/$id|Sign up>"
        assertThat(service.send(event.id, preview.version, message)).isEqualTo(EventReminderResult(1, 0, 0, 0))
        verify(gateway).remind(eq("U_SECOND"), eq(message), any())
        verify(store, never()).claim(eq(id), eq(first.id), any(), any())
        whenever(store.deliveries(id)).thenReturn(listOf(
            ReminderDelivery(first.id, ReminderDeliveryStatus.SENT, now),
            ReminderDelivery(second.id, ReminderDeliveryStatus.UNCERTAIN, now),
        ))
        val refreshed = service.preview(event.id)
        assertThatThrownBy { service.send(event.id, refreshed.version, "Another edit") }
            .isInstanceOf(EventReminderConflictException::class.java)
        verify(gateway, times(1)).remind(any(), any(), any())
    }

    @Test
    fun `delivery history is isolated to the selected Delta event`() {
        val otherId = UUID.randomUUID()
        val other = event.copy(id = otherId.toString(), name = "Other event")
        whenever(catalog.getAllEvents()).thenReturn(listOf(event, other))
        whenever(source.signupRoster(otherId)).thenReturn(roster().copy(eventId = otherId))
        whenever(store.deliveries(id)).thenReturn(listOf(
            ReminderDelivery(first.id, ReminderDeliveryStatus.SENT, now),
            ReminderDelivery(second.id, ReminderDeliveryStatus.SENT, now),
        ))
        whenever(store.deliveries(otherId)).thenReturn(emptyList())
        assertThat(service.preview(event.id).recipients).allMatch { it.status == ReminderRecipientStatus.ALREADY_SENT }
        val preview = service.preview(other.id)
        assertThat(service.send(other.id, preview.version, "Other event reminder")).isEqualTo(EventReminderResult(2, 0, 0, 0))
        verify(store, times(2)).claim(eq(otherId), any(), any(), any())
        verify(store, never()).claim(eq(id), any(), any(), any())
    }

    @Test
    fun `blank and oversized messages are rejected before checking recipients or claiming deliveries`() {
        for (message in listOf("", " \n\t", "x".repeat(4001))) {
            assertThatThrownBy { service.validate(event.id, "version", message) }
                .isInstanceOf(InvalidEventReminderMessageException::class.java)
            assertThatThrownBy { service.send(event.id, "version", message) }
                .isInstanceOf(InvalidEventReminderMessageException::class.java)
        }
        verifyNoInteractions(catalog, source, directory, store, gateway)
    }

    @Test
    fun `a message at the length limit is delivered unchanged`() {
        val message = "x".repeat(4000)
        service.send(event.id, service.preview(event.id).version, message)
        verify(gateway, times(2)).remind(any(), eq(message), any())
    }
}
