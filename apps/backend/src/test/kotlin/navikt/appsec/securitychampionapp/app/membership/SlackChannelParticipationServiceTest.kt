package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.DeactivationReason
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SlackChannelParticipationServiceTest {
    private val now = Instant.parse("2026-10-09T07:00:00Z")
    private val participants = mock<ParticipantStore>()
    private val directory = mock<SlackParticipantDirectory>()
    private val slack = mock<SlackChannelGateway>()
    private val store = mock<SlackChannelParticipationStore>()
    private val service = SlackChannelParticipationService(
        participants, directory, slack, store,
        SlackChannelParticipationSettings("C_CHANNEL", 2), Clock.fixed(now, ZoneOffset.UTC),
    )
    private val active = participant("active")

    @Test
    fun `participants absent without an earlier presence are recorded for review only`() {
        given(listOf(active), mapOf(active.id to "U_ACTIVE"), setOf("U_OTHER"), emptyMap())

        val summary = service.check()

        val plan = appliedPlan()
        assertThat(plan.departures).isEmpty()
        assertThat(plan.observations).containsExactly(ChannelObservation(active.id, "U_ACTIVE", false, now))
        assertThat(summary.absent).isEqualTo(1)
    }

    @Test
    fun `active participant previously observed in the channel is deactivated after leaving`() {
        given(
            listOf(active), mapOf(active.id to "U_ACTIVE"), setOf("U_OTHER"),
            mapOf(active.id to ChannelObservation(active.id, "U_ACTIVE", true, null)),
        )

        service.check()

        assertThat(appliedPlan().departures).containsExactlyEntriesOf(mapOf(active.id to "U_ACTIVE"))
    }

    @Test
    fun `an absence keeps its first observed time and a changed identity starts a new baseline`() {
        val earlier = now.minus(Duration.ofDays(3))
        val other = participant("other")
        given(
            listOf(active, other), mapOf(active.id to "U_ACTIVE", other.id to "U_NEW"), setOf("U_THIRD"),
            mapOf(
                active.id to ChannelObservation(active.id, "U_ACTIVE", false, earlier),
                other.id to ChannelObservation(other.id, "U_OLD", true, null),
            ),
        )

        service.check()

        val plan = appliedPlan()
        assertThat(plan.departures).isEmpty()
        assertThat(plan.observations).contains(
            ChannelObservation(active.id, "U_ACTIVE", false, earlier),
            ChannelObservation(other.id, "U_NEW", false, now),
        )
    }

    @Test
    fun `unresolved identities are never treated as channel departures`() {
        given(
            listOf(active), emptyMap(), setOf("U_OTHER"),
            mapOf(active.id to ChannelObservation(active.id, "U_ACTIVE", true, null)),
        )

        val summary = service.check()

        assertThat(appliedPlan().departures).isEmpty()
        assertThat(appliedPlan().observations).containsExactly(ChannelObservation(active.id, null, null, null))
        assertThat(summary.unresolved).isEqualTo(1)
    }

    @Test
    fun `only channel-caused deactivations are checked and returned participants are reactivated`() {
        val departed = participant("departed", ParticipationStatus.DEACTIVATED, DeactivationReason.SLACK_CHANNEL_DEPARTURE)
        val adminDeactivated = participant("admin", ParticipationStatus.DEACTIVATED)
        val left = participant("left", ParticipationStatus.LEFT)
        given(
            listOf(departed, adminDeactivated, left),
            mapOf(departed.id to "U_DEPARTED"),
            setOf("U_DEPARTED", "U_ADMIN", "U_LEFT"),
            emptyMap(),
        )

        service.check()

        verify(directory).resolve(listOf(departed))
        assertThat(appliedPlan().returns).containsExactly(departed.id)
    }

    @Test
    fun `unexpectedly many departures abort before any participant changes`() {
        val all = (1..3).map { participant("p$it") }
        given(
            all, all.associate { it.id to "U_${it.id}" }, setOf("U_OTHER"),
            all.associate { it.id to ChannelObservation(it.id, "U_${it.id}", true, null) },
        )

        assertThatThrownBy { service.check() }.isInstanceOf(ChannelDepartureLimitException::class.java)

        verify(store, never()).apply(any(), any(), any())
        verify(slack, never()).notifyChannelDeparture(any(), any(), any())
    }

    @Test
    fun `an empty channel snapshot is rejected`() {
        given(listOf(active), mapOf(active.id to "U_ACTIVE"), emptySet(), emptyMap())

        assertThatThrownBy { service.check() }.isInstanceOf(IllegalStateException::class.java)

        verify(store, never()).apply(any(), any(), any())
    }

    @Test
    fun `departure notices are sent while deactivation remains and cancelled after reactivation`() {
        val departed = participant("departed", ParticipationStatus.DEACTIVATED, DeactivationReason.SLACK_CHANNEL_DEPARTURE)
        val sendable = ChannelDepartureNotice(UUID.randomUUID(), departed.id, "U_DEPARTED", ChannelNoticeStatus.PENDING)
        val stale = ChannelDepartureNotice(UUID.randomUUID(), active.id, "U_ACTIVE", ChannelNoticeStatus.PENDING)
        given(listOf(active), mapOf(active.id to "U_ACTIVE"), setOf("U_ACTIVE"), emptyMap())
        whenever(store.dueNotices("C_CHANNEL")).thenReturn(listOf(sendable, stale))
        whenever(participants.findById(departed.id)).thenReturn(departed)
        whenever(participants.findById(active.id)).thenReturn(active)
        whenever(slack.notifyChannelDeparture("U_DEPARTED", "C_CHANNEL", sendable.id)).thenReturn("123.456")

        val summary = service.check()

        val order = inOrder(store, slack)
        order.verify(store).recoverInterruptedNotices("C_CHANNEL")
        order.verify(store).updateNotice(sendable.id, ChannelNoticeStatus.SENDING)
        order.verify(slack).notifyChannelDeparture("U_DEPARTED", "C_CHANNEL", sendable.id)
        order.verify(store).updateNotice(sendable.id, ChannelNoticeStatus.SENT, "123.456")
        verify(store).updateNotice(stale.id, ChannelNoticeStatus.CANCELLED)
        assertThat(summary.noticesSent).isEqualTo(1)
    }

    @Test
    fun `ambiguous notice delivery is marked unconfirmed and not retried in the same run`() {
        val departed = participant("departed", ParticipationStatus.DEACTIVATED, DeactivationReason.SLACK_CHANNEL_DEPARTURE)
        val first = ChannelDepartureNotice(UUID.randomUUID(), departed.id, "U_DEPARTED", ChannelNoticeStatus.PENDING)
        val second = ChannelDepartureNotice(UUID.randomUUID(), departed.id, "U_DEPARTED", ChannelNoticeStatus.PENDING)
        given(listOf(active), mapOf(active.id to "U_ACTIVE"), setOf("U_ACTIVE"), emptyMap())
        whenever(store.dueNotices("C_CHANNEL")).thenReturn(listOf(first, second))
        whenever(participants.findById(departed.id)).thenReturn(departed)
        whenever(slack.notifyChannelDeparture(any(), any(), eq(first.id)))
            .thenThrow(MembershipDeliveryException(true, "unknown"))

        service.check()

        verify(store).updateNotice(first.id, ChannelNoticeStatus.UNCERTAIN)
        verify(slack, never()).notifyChannelDeparture(any(), any(), eq(second.id))
    }

    private fun given(
        all: List<ProgramParticipant>,
        resolved: Map<UUID, String>,
        members: Set<String>,
        previous: Map<UUID, ChannelObservation>,
    ) {
        whenever(participants.findAllParticipants()).thenReturn(all)
        whenever(slack.channelMembers("C_CHANNEL")).thenReturn(members)
        whenever(directory.resolve(any())).thenAnswer { invocation ->
            val requested = invocation.getArgument<List<ProgramParticipant>>(0).map { it.id }.toSet()
            SlackIdentityResolution(resolved.filterKeys { it in requested }, requested - resolved.keys)
        }
        whenever(store.observations("C_CHANNEL")).thenReturn(previous)
        whenever(store.apply(eq("C_CHANNEL"), eq(now), any())).thenReturn(ChannelCheckChanges(emptySet(), emptySet()))
    }

    private fun appliedPlan(): ChannelCheckPlan {
        val plan = argumentCaptor<ChannelCheckPlan>()
        verify(store).apply(eq("C_CHANNEL"), eq(now), plan.capture())
        return plan.firstValue
    }

    private fun participant(
        name: String,
        status: ParticipationStatus = ParticipationStatus.ACTIVE,
        reason: DeactivationReason? = null,
    ) = ProgramParticipant(
        UUID.randomUUID(), "$name@nav.no", "A12345", "$name@nav.no", name, emptyList(), status, "2026-10-07", reason,
    )
}
