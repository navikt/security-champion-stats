package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersUpdateRequest
import com.slack.api.methods.request.users.UsersInfoRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersUpdateResponse
import com.slack.api.methods.response.users.UsersInfoResponse
import com.slack.api.methods.response.users.UsersLookupByEmailResponse
import com.slack.api.model.User
import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.app.scoring.MappedSlackParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.SlackIdentityMappingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.io.IOException
import java.time.Instant
import java.time.Duration
import java.util.UUID
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

class SlackMembershipClientTest {
    private val client = mock<MethodsClient>()
    private val mappings = mock<SlackIdentityMappingRepository>()
    private val service = SlackMembershipClient(client, mappings, SlackApiService(client))
    private val participant = ProgramParticipant(
        UUID.randomUUID(), "participant@nav.no", "A12345", "participant@nav.no",
        "Participant", emptyList(), ParticipationStatus.ACTIVE, "2026-10-07",
    )

    @Test
    fun `group replacement sends the entire desired membership and rejects empty membership`() {
        whenever(client.usergroupsUsersUpdate(any<UsergroupsUsersUpdateRequest>()))
            .thenReturn(UsergroupsUsersUpdateResponse().apply { isOk = true })

        service.replaceMembers("S_GROUP", setOf("U_TWO", "U_ONE"))

        val request = argumentCaptor<UsergroupsUsersUpdateRequest>()
        verify(client).usergroupsUsersUpdate(request.capture())
        assertThat(request.firstValue.usergroup).isEqualTo("S_GROUP")
        assertThat(request.firstValue.users).containsExactly("U_ONE", "U_TWO")
        assertThatThrownBy { service.replaceMembers("S_GROUP", emptySet()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `email lookup finds participants who have never posted a scoring message`() {
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersLookupByEmail(any<UsersLookupByEmailRequest>()))
            .thenReturn(UsersLookupByEmailResponse().apply { isOk = true; user = human() })

        val result = service.resolve(listOf(participant))

        assertThat(result.users).containsEntry(participant.id, "U_PERSON")
        assertThat(result.unresolvedParticipantIds).isEmpty()
        val request = argumentCaptor<UsersLookupByEmailRequest>()
        verify(client).usersLookupByEmail(request.capture())
        assertThat(request.firstValue.email).isEqualTo(participant.navNoEmail)
    }

    @Test
    fun `approved mappings take precedence over automatic email lookup`() {
        whenever(mappings.mappedParticipants()).thenReturn(mapOf(
            "U_APPROVED" to MappedSlackParticipant("U_APPROVED", participant.id, true, Instant.EPOCH),
        ))
        whenever(client.usersInfo(any<UsersInfoRequest>())).thenReturn(
            UsersInfoResponse().apply { isOk = true; user = human().apply { id = "U_APPROVED" } },
        )

        assertThat(service.resolve(listOf(participant)).users).containsEntry(participant.id, "U_APPROVED")
        verify(client, never()).usersLookupByEmail(any<UsersLookupByEmailRequest>())
    }

    @Test
    fun `multiple approved accounts remain unresolved instead of arbitrarily choosing one`() {
        whenever(mappings.mappedParticipants()).thenReturn(mapOf(
            "U_FIRST" to MappedSlackParticipant("U_FIRST", participant.id, true, Instant.EPOCH),
            "U_SECOND" to MappedSlackParticipant("U_SECOND", participant.id, true, Instant.EPOCH),
        ))

        assertThat(service.resolve(listOf(participant)).unresolvedParticipantIds).containsExactly(participant.id)
        verifyNoInteractions(client)
    }

    @ParameterizedTest
    @ValueSource(strings = ["deleted", "bot", "guest", "single-channel-guest"])
    fun `ineligible Slack accounts are reported as unresolved`(type: String) {
        val account = human().apply {
            when (type) {
                "deleted" -> isDeleted = true
                "bot" -> isBot = true
                "guest" -> isRestricted = true
                "single-channel-guest" -> isUltraRestricted = true
            }
        }
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersLookupByEmail(any<UsersLookupByEmailRequest>()))
            .thenReturn(UsersLookupByEmailResponse().apply { isOk = true; user = account })

        val result = service.resolve(listOf(participant))

        assertThat(result.users).isEmpty()
        assertThat(result.unresolvedParticipantIds).containsExactly(participant.id)
    }

    @Test
    fun `a Slack account assigned to two participants blocks both identities`() {
        val other = participant.copy(id = UUID.randomUUID())
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersLookupByEmail(any<UsersLookupByEmailRequest>()))
            .thenReturn(UsersLookupByEmailResponse().apply { isOk = true; user = human() })

        val result = service.resolve(listOf(participant, other))

        assertThat(result.users).isEmpty()
        assertThat(result.unresolvedParticipantIds).containsExactlyInAnyOrder(participant.id, other.id)
    }

    @Test
    fun `lookup failures cannot become missing-account results`() {
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersLookupByEmail(any<UsersLookupByEmailRequest>()))
            .thenReturn(UsersLookupByEmailResponse().apply { isOk = false; error = "missing_scope" })

        assertThatThrownBy { service.resolve(listOf(participant)) }
            .isInstanceOf(SlackIntegrationException::class.java)
            .hasMessageContaining("missing_scope")
    }

    @Test
    fun `successful announcements retain Slack timestamp and delivery metadata`() {
        val deliveryId = UUID.randomUUID()
        whenever(client.chatPostMessage(any<ChatPostMessageRequest>()))
            .thenReturn(ChatPostMessageResponse().apply { isOk = true; ts = "123.456" })

        assertThat(service.announce("C_DESTINATION", "U_PERSON", MembershipAnnouncementKind.WELCOME, deliveryId))
            .isEqualTo("123.456")

        val request = argumentCaptor<ChatPostMessageRequest>()
        verify(client).chatPostMessage(request.capture())
        assertThat(request.firstValue.channel).isEqualTo("C_DESTINATION")
        assertThat(request.firstValue.text).contains("<@U_PERSON>")
        assertThat(request.firstValue.metadataAsString).contains(deliveryId.toString())
    }

    @Test
    fun `network failure during announcement is uncertain and is not retried`() {
        whenever(client.chatPostMessage(any<ChatPostMessageRequest>())).thenThrow(IOException("Connection lost"))

        assertThatThrownBy {
            service.announce("C_DESTINATION", "U_PERSON", MembershipAnnouncementKind.REMOVAL, UUID.randomUUID())
        }.isInstanceOfSatisfying(MembershipDeliveryException::class.java) { assertThat(it.uncertain).isTrue() }

        verify(client, times(1)).chatPostMessage(any<ChatPostMessageRequest>())
    }

    @Test
    fun `explicit Slack rejection is safely retryable without claiming success`() {
        whenever(client.chatPostMessage(any<ChatPostMessageRequest>()))
            .thenReturn(ChatPostMessageResponse().apply { isOk = false; error = "not_in_channel" })

        assertThatThrownBy {
            service.announce("C_DESTINATION", "U_PERSON", MembershipAnnouncementKind.WELCOME, UUID.randomUUID())
        }.isInstanceOfSatisfying(MembershipDeliveryException::class.java) { assertThat(it.uncertain).isFalse() }
    }

    @Test
    fun `identity lookups honor rate limits without restarting the entire reconciliation`() {
        val sleeps = mutableListOf<Duration>()
        val limited = SlackApiException(
            Response.Builder().request(Request.Builder().url("https://slack.com/api/users.lookupByEmail").build())
                .protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests").header("Retry-After", "2").build(),
            """{"ok":false,"error":"ratelimited"}""",
        )
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersLookupByEmail(any<UsersLookupByEmailRequest>()))
            .thenThrow(limited)
            .thenReturn(UsersLookupByEmailResponse().apply { isOk = true; user = human() })

        val result = SlackMembershipClient(client, mappings, SlackApiService(client) { sleeps += it })
            .resolve(listOf(participant))

        assertThat(result.users).containsEntry(participant.id, "U_PERSON")
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2))
        verify(client, times(2)).usersLookupByEmail(any<UsersLookupByEmailRequest>())
    }

    private fun human() = User().apply {
        id = "U_PERSON"
        profile = User.Profile().apply { email = participant.navNoEmail }
    }
}
