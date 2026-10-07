package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import java.util.UUID
import java.time.Instant
import java.time.Duration

class SlackMembershipServiceTest {
    private val participants = mock<ParticipantStore>()
    private val directory = mock<SlackParticipantDirectory>()
    private val slack = mock<SlackMembershipGateway>()
    private val roles = mock<ChampionRoleSource>()
    private val state = mock<SlackMembershipStore>()
    private val settings = SlackMembershipSettings("S_GROUP", "C_WELCOME", "C_ADMIN")
    private val service = SlackMembershipService(participants, directory, slack, roles, state, settings)
    private val participant = ProgramParticipant(
        UUID.randomUUID(), "participant@nav.no", "A12345", "participant@nav.no",
        "Participant", emptyList(), ParticipationStatus.ACTIVE, "2026-10-07",
    )

    @Test
    fun `zero active participants fails before any Slack or state writes`() {
        whenever(participants.findActiveParticipants()).thenReturn(emptyList())

        assertThatThrownBy { service.sync(dryRun = false) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("zero active participants")

        verifyNoInteractions(directory, slack, roles, state)
    }

    @Test
    fun `silent baseline reconciles the group without announcements`() {
        whenever(participants.findActiveParticipants()).thenReturn(listOf(participant))
        whenever(directory.resolve(listOf(participant)))
            .thenReturn(SlackIdentityResolution(mapOf(participant.id to "U_ACTIVE"), emptySet()))
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_OLD"))
        whenever(state.announcements("S_GROUP")).thenReturn(emptyList())

        val result = service.sync(dryRun = false)

        assertThat(result.addedUserIds).containsExactly("U_ACTIVE")
        assertThat(result.removedUserIds).containsExactly("U_OLD")
        verify(state).observe("S_GROUP", mapOf(participant.id to "U_ACTIVE"))
        verify(slack).replaceMembers("S_GROUP", setOf("U_ACTIVE"))
        verify(slack, never()).announce(any(), any(), any(), any())
    }

    @Test
    fun `active participant missing the role receives a welcome in the configured channel`() {
        val announcement = MembershipAnnouncement(
            UUID.randomUUID(), participant.id, "U_ACTIVE",
            MembershipAnnouncementKind.WELCOME, MembershipDeliveryStatus.PENDING,
        )
        whenever(participants.findActiveParticipants()).thenReturn(listOf(participant))
        whenever(participants.findByNavNoEmail(participant.navNoEmail)).thenReturn(participant)
        whenever(participants.findById(participant.id)).thenReturn(participant)
        whenever(directory.resolve(any()))
            .thenReturn(SlackIdentityResolution(mapOf(participant.id to "U_ACTIVE"), emptySet()))
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_ACTIVE"))
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        whenever(roles.fetchRoles()).thenReturn(mapOf(participant.navNoEmail to ChampionRole.ABSENT))
        whenever(slack.announce("C_WELCOME", "U_ACTIVE", announcement.kind, announcement.id))
            .thenReturn("123.456")

        service.sync(dryRun = false)

        val order = inOrder(state, slack)
        order.verify(state).updateDelivery(announcement.id, MembershipDeliveryStatus.SENDING)
        order.verify(slack).announce("C_WELCOME", "U_ACTIVE", announcement.kind, announcement.id)
        order.verify(state).updateDelivery(announcement.id, MembershipDeliveryStatus.SENT, "123.456")
        verify(slack, never()).replaceMembers(any(), any())
        verify(participants, never()).findAllParticipants()
    }

    @ParameterizedTest
    @EnumSource(ChampionRole::class, names = ["PRESENT", "UNKNOWN"])
    fun `role holders are suppressed and unknown roles remain pending`(role: ChampionRole) {
        prepare()
        val announcement = pending(MembershipAnnouncementKind.WELCOME)
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_OLD"))
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        whenever(roles.fetchRoles()).thenReturn(mapOf(participant.navNoEmail to role))

        service.sync(false)

        verify(slack, never()).announce(any(), any(), any(), any())
        verify(slack).replaceMembers("S_GROUP", setOf("U_ACTIVE"))
        if (role == ChampionRole.PRESENT) {
            verify(state).updateDelivery(announcement.id, MembershipDeliveryStatus.SUPPRESSED)
        } else {
            verify(state, never()).updateDelivery(any(), any(), anyOrNull())
        }
    }

    @ParameterizedTest
    @EnumSource(ParticipationStatus::class, names = ["LEFT", "DEACTIVATED"])
    fun `removal uses current role and goes to configured admin channel`(status: ParticipationStatus) {
        prepare()
        val removed = participant.copy(id = UUID.randomUUID(), navNoEmail = "removed@nav.no", status = status)
        val announcement = pending(MembershipAnnouncementKind.REMOVAL).copy(participantId = removed.id)
        whenever(participants.findById(removed.id)).thenReturn(removed)
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        whenever(roles.fetchRoles()).thenReturn(mapOf(removed.navNoEmail to ChampionRole.ABSENT))
        whenever(slack.announce(any(), any(), any(), any())).thenReturn("123.456")

        service.sync(false)

        verify(slack).announce("C_ADMIN", "U_ACTIVE", MembershipAnnouncementKind.REMOVAL, announcement.id)
    }

    @Test
    fun `dry run reports differences without persisting or sending`() {
        prepare()
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_OLD"))

        val result = service.sync(true)

        assertThat(result.removedUserIds).containsExactly("U_OLD")
        verifyNoInteractions(state, roles)
        verify(slack, never()).replaceMembers(any(), any())
        verify(slack, never()).announce(any(), any(), any(), any())
    }

    @Test
    fun `unresolved identities block all replacement and transition writes`() {
        prepare()
        whenever(directory.resolve(any())).thenReturn(SlackIdentityResolution(emptyMap(), setOf(participant.id)))

        assertThatThrownBy { service.sync(false) }.hasMessageContaining("unresolved participant identities")

        verifyNoInteractions(state, roles)
        verify(slack, never()).replaceMembers(any(), any())
    }

    @Test
    fun `membership change during lookup blocks stale replacement`() {
        prepare()
        whenever(participants.findActiveParticipants()).thenReturn(listOf(participant), emptyList())

        assertThatThrownBy { service.sync(false) }.hasMessageContaining("Participation changed")

        verifyNoInteractions(state, roles)
        verify(slack, never()).replaceMembers(any(), any())
    }

    @Test
    fun `group failure leaves recorded transitions for the next run and sends nothing`() {
        prepare()
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_OLD"))
        doThrow(IllegalStateException("Slack unavailable")).whenever(slack).replaceMembers(any(), any())

        assertThatThrownBy { service.sync(false) }.hasMessageContaining("Slack unavailable")

        verify(state).observe("S_GROUP", mapOf(participant.id to "U_ACTIVE"))
        verifyNoInteractions(roles)
        verify(slack, never()).announce(any(), any(), any(), any())
    }

    @Test
    fun `Teamkatalogen failure defers announcements without preventing group reconciliation`() {
        prepare()
        val announcement = pending(MembershipAnnouncementKind.WELCOME)
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_OLD"))
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        whenever(roles.fetchRoles()).thenThrow(IllegalStateException("Teamkatalogen unavailable"))

        assertThatThrownBy { service.sync(false) }.hasMessageContaining("Teamkatalogen unavailable")

        verify(slack).replaceMembers("S_GROUP", setOf("U_ACTIVE"))
        verify(slack, never()).announce(any(), any(), any(), any())
        verify(state, never()).updateDelivery(any(), any(), anyOrNull())
    }

    @ParameterizedTest
    @EnumSource(MembershipDeliveryStatus::class, names = ["UNCERTAIN", "SENDING"])
    fun `ambiguous or interrupted deliveries are never automatically resent`(status: MembershipDeliveryStatus) {
        prepare()
        whenever(state.announcements("S_GROUP"))
            .thenReturn(listOf(pending(MembershipAnnouncementKind.WELCOME).copy(status = status)))

        service.sync(false)

        verify(state).recoverInterruptedDeliveries("S_GROUP")
        verifyNoInteractions(roles)
        verify(slack, never()).announce(any(), any(), any(), any())
    }

    @Test
    fun `network ambiguity is persisted without claiming delivery`() {
        prepare()
        val announcement = pending(MembershipAnnouncementKind.WELCOME)
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        whenever(roles.fetchRoles()).thenReturn(mapOf(participant.navNoEmail to ChampionRole.ABSENT))
        whenever(slack.announce(any(), any(), any(), any()))
            .thenThrow(MembershipDeliveryException(true, "Unknown delivery"))

        assertThatThrownBy { service.sync(false) }.hasMessageContaining("Unknown delivery")

        verify(state).updateDelivery(announcement.id, MembershipDeliveryStatus.UNCERTAIN)
        verify(state, never()).updateDelivery(eq(announcement.id), eq(MembershipDeliveryStatus.SENT), anyOrNull())
    }

    @Test
    fun `rejected recipient does not starve later announcements`() {
        prepare()
        val rejected = pending(MembershipAnnouncementKind.WELCOME)
        val next = pending(MembershipAnnouncementKind.WELCOME)
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(rejected, next))
        whenever(roles.fetchRoles()).thenReturn(mapOf(participant.navNoEmail to ChampionRole.ABSENT))
        whenever(slack.announce("C_WELCOME", "U_ACTIVE", rejected.kind, rejected.id))
            .thenThrow(MembershipDeliveryException(false, "invalid_user", stopBatch = false))
        whenever(slack.announce("C_WELCOME", "U_ACTIVE", next.kind, next.id)).thenReturn("123.456")

        service.sync(false)

        verify(slack).announce("C_WELCOME", "U_ACTIVE", next.kind, next.id)
        verify(state).updateDelivery(next.id, MembershipDeliveryStatus.SENT, "123.456")
        verify(state).deferDelivery(rejected.id, Duration.ofMinutes(15))
    }

    @Test
    fun `deferred deliveries are not retried before their due time`() {
        prepare()
        val announcement = pending(MembershipAnnouncementKind.WELCOME).copy(nextAttemptAt = Instant.MAX)
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        service.sync(false)
        verifyNoInteractions(roles)
        verify(slack, never()).announce(any(), any(), any(), any())
    }

    @Test
    fun `shared configuration rejection stops the batch after persisting retry time`() {
        prepare()
        val first = pending(MembershipAnnouncementKind.WELCOME)
        val next = pending(MembershipAnnouncementKind.WELCOME)
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(first, next))
        whenever(roles.fetchRoles()).thenReturn(mapOf(participant.navNoEmail to ChampionRole.ABSENT))
        whenever(slack.announce(any(), any(), any(), any()))
            .thenThrow(MembershipDeliveryException(false, "not_in_channel", stopBatch = true))
        assertThatThrownBy { service.sync(false) }.hasMessageContaining("not_in_channel")
        verify(state).deferDelivery(first.id, Duration.ofMinutes(15))
        verify(slack, never()).announce(any(), any(), any(), eq(next.id))
    }

    @Test
    fun `manual write is bound to membership identity and enrollment snapshot`() {
        prepare()
        val version = service.sync(true).version
        assertThat(version).hasSize(64)
        service.validatePreview(version)
        service.sync(false, version)
        verify(state).observe("S_GROUP", mapOf(participant.id to "U_ACTIVE"))
    }

    @Test
    fun `changed Slack membership invalidates preview before writes`() {
        prepare()
        val version = service.sync(true).version
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_EXTERNAL"))
        assertThatThrownBy { service.sync(false, version) }.isInstanceOf(MembershipPreviewChangedException::class.java)
        verifyNoInteractions(state, roles)
        verify(slack, never()).replaceMembers(any(), any())
    }

    @Test
    fun `changed mapping invalidates preview even when group differences remain identical`() {
        prepare()
        val other = participant.copy(id = UUID.randomUUID(), navNoEmail = "other@nav.no")
        whenever(participants.findActiveParticipants()).thenReturn(listOf(participant, other))
        whenever(directory.resolve(any())).thenReturn(
            SlackIdentityResolution(mapOf(participant.id to "U_ACTIVE", other.id to "U_OTHER"), emptySet()),
        )
        val version = service.sync(true).version
        whenever(directory.resolve(any())).thenReturn(
            SlackIdentityResolution(mapOf(participant.id to "U_OTHER", other.id to "U_ACTIVE"), emptySet()),
        )
        assertThatThrownBy { service.sync(false, version) }.isInstanceOf(MembershipPreviewChangedException::class.java)
        verifyNoInteractions(state, roles)
    }

    @Test
    fun `enrollment change invalidates preview`() {
        prepare()
        val version = service.sync(true).version
        whenever(participants.findActiveParticipants()).thenReturn(listOf(participant.copy(navNoEmail = "changed@nav.no")))
        assertThatThrownBy { service.sync(false, version) }.isInstanceOf(MembershipPreviewChangedException::class.java)
        verifyNoInteractions(state, roles)
    }

    @Test
    fun `missing preview cannot validate a manual write`() {
        assertThatThrownBy { service.validatePreview(null) }.isInstanceOf(MembershipPreviewChangedException::class.java)
        assertThatThrownBy { service.validatePreview("") }.isInstanceOf(MembershipPreviewChangedException::class.java)
        verifyNoInteractions(participants, slack, directory, state)
    }

    @Test
    fun `pending welcome is cancelled when participant has left before delivery`() {
        prepare()
        val announcement = pending(MembershipAnnouncementKind.WELCOME)
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        whenever(participants.findById(participant.id)).thenReturn(participant.copy(status = ParticipationStatus.LEFT))
        whenever(roles.fetchRoles()).thenReturn(emptyMap())

        service.sync(false)

        verify(state).updateDelivery(announcement.id, MembershipDeliveryStatus.CANCELLED)
        verify(slack, never()).announce(any(), any(), any(), any())
    }

    @Test
    fun `deferred welcome follows a corrected Slack mapping rather than mentioning the old account`() {
        prepare()
        val announcement = pending(MembershipAnnouncementKind.WELCOME).copy(slackUserId = "U_OLD_ACCOUNT")
        whenever(state.announcements("S_GROUP")).thenReturn(listOf(announcement))
        whenever(roles.fetchRoles()).thenReturn(mapOf(participant.navNoEmail to ChampionRole.ABSENT))
        whenever(slack.announce(any(), any(), any(), any())).thenReturn("123.456")

        service.sync(false)

        verify(slack).announce("C_WELCOME", "U_ACTIVE", announcement.kind, announcement.id)
    }

    private fun prepare() {
        whenever(participants.findActiveParticipants()).thenReturn(listOf(participant))
        whenever(participants.findById(participant.id)).thenReturn(participant)
        whenever(directory.resolve(any()))
            .thenReturn(SlackIdentityResolution(mapOf(participant.id to "U_ACTIVE"), emptySet()))
        whenever(slack.members("S_GROUP")).thenReturn(setOf("U_ACTIVE"))
        whenever(state.announcements("S_GROUP")).thenReturn(emptyList())
    }

    private fun pending(kind: MembershipAnnouncementKind) = MembershipAnnouncement(
        UUID.randomUUID(), participant.id, "U_ACTIVE", kind, MembershipDeliveryStatus.PENDING,
    )
}
