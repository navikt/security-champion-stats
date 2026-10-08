package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersUpdateRequest
import com.slack.api.methods.request.users.UsersInfoRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.methods.request.users.UsersListRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersUpdateResponse
import com.slack.api.methods.response.users.UsersListResponse
import com.slack.api.model.User
import com.slack.api.model.ResponseMetadata
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
        whenever(client.usersList(any<UsersListRequest>())).thenReturn(page(listOf(human())))

        val result = service.resolve(listOf(participant))

        assertThat(result.users).containsEntry(participant.id, "U_PERSON")
        assertThat(result.unresolvedParticipantIds).isEmpty()
        verify(client, times(1)).usersList(any<UsersListRequest>())
        verify(client, never()).usersLookupByEmail(any<UsersLookupByEmailRequest>())
        verify(client, never()).usersInfo(any<UsersInfoRequest>())
    }

    @Test
    fun `approved mappings take precedence over automatic email lookup`() {
        whenever(mappings.mappedParticipants()).thenReturn(mapOf(
            "U_APPROVED" to MappedSlackParticipant("U_APPROVED", participant.id, true, Instant.EPOCH),
        ))
        whenever(client.usersList(any<UsersListRequest>())).thenReturn(
            page(listOf(human().apply { id = "U_APPROVED"; profile.email = "different@nav.no" }, human())),
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
        whenever(client.usersList(any<UsersListRequest>())).thenReturn(page(emptyList()))

        assertThat(service.resolve(listOf(participant)).unresolvedParticipantIds).containsExactly(participant.id)
        verify(client, times(1)).usersList(any<UsersListRequest>())
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
        whenever(client.usersList(any<UsersListRequest>())).thenReturn(page(listOf(account)))

        val result = service.resolve(listOf(participant))

        assertThat(result.users).isEmpty()
        assertThat(result.unresolvedParticipantIds).containsExactly(participant.id)
    }

    @Test
    fun `a Slack account assigned to two participants blocks both identities`() {
        val other = participant.copy(id = UUID.randomUUID())
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersList(any<UsersListRequest>())).thenReturn(page(listOf(human())))

        val result = service.resolve(listOf(participant, other))

        assertThat(result.users).isEmpty()
        assertThat(result.unresolvedParticipantIds).containsExactlyInAnyOrder(participant.id, other.id)
    }

    @Test
    fun `lookup failures cannot become missing-account results`() {
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersList(any<UsersListRequest>()))
            .thenReturn(UsersListResponse().apply { isOk = false; error = "missing_scope" })

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
    fun `event reminders send one direct message with delivery metadata and no link unfurling`() {
        val deliveryId = UUID.randomUUID()
        whenever(client.chatPostMessage(any<ChatPostMessageRequest>()))
            .thenReturn(ChatPostMessageResponse().apply { isOk = true; ts = "123.456" })
        assertThat(service.remind("U_PERSON", "Synthetic event reminder", deliveryId)).isEqualTo("123.456")
        val request = argumentCaptor<ChatPostMessageRequest>()
        verify(client).chatPostMessage(request.capture())
        assertThat(request.firstValue.channel).isEqualTo("U_PERSON")
        assertThat(request.firstValue.text).isEqualTo("Synthetic event reminder")
        assertThat(request.firstValue.isUnfurlLinks).isFalse()
        assertThat(request.firstValue.isUnfurlMedia).isFalse()
        assertThat(request.firstValue.metadataAsString).contains("security_champion_event_reminder", deliveryId.toString())
        verifyNoMoreInteractions(client)
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
        }.isInstanceOfSatisfying(MembershipDeliveryException::class.java) {
            assertThat(it.uncertain).isFalse()
            assertThat(it.stopBatch).isTrue()
        }
    }

    @Test
    fun `recipient rejection allows remaining messages to proceed`() {
        whenever(client.chatPostMessage(any<ChatPostMessageRequest>()))
            .thenReturn(ChatPostMessageResponse().apply { isOk = false; error = "invalid_user" })
        assertThatThrownBy {
            service.announce("C_DESTINATION", "U_PERSON", MembershipAnnouncementKind.WELCOME, UUID.randomUUID())
        }.isInstanceOfSatisfying(MembershipDeliveryException::class.java) {
            assertThat(it.uncertain).isFalse()
            assertThat(it.stopBatch).isFalse()
        }
    }

    @Test
    fun `message rate limit is deferred without automatic resend and honors Retry-After`() {
        val limited = SlackApiException(
            Response.Builder().request(Request.Builder().url("https://slack.com/api/chat.postMessage").build())
                .protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests").header("Retry-After", "1200").build(),
            """{"ok":false,"error":"ratelimited"}""",
        )
        whenever(client.chatPostMessage(any<ChatPostMessageRequest>())).thenThrow(limited)
        assertThatThrownBy {
            service.announce("C_DESTINATION", "U_PERSON", MembershipAnnouncementKind.WELCOME, UUID.randomUUID())
        }.isInstanceOfSatisfying(MembershipDeliveryException::class.java) {
            assertThat(it.uncertain).isFalse()
            assertThat(it.stopBatch).isTrue()
            assertThat(it.retryAfter).isEqualTo(Duration.ofSeconds(1200))
        }
        verify(client, times(1)).chatPostMessage(any<ChatPostMessageRequest>())
    }

    @Test
    fun `identity lookups honor rate limits without restarting the entire reconciliation`() {
        val sleeps = mutableListOf<Duration>()
        val limited = SlackApiException(
            Response.Builder().request(Request.Builder().url("https://slack.com/api/users.list").build())
                .protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests").header("Retry-After", "2").build(),
            """{"ok":false,"error":"ratelimited"}""",
        )
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersList(any<UsersListRequest>()))
            .thenThrow(limited)
            .thenReturn(page(listOf(human())))

        val result = SlackMembershipClient(client, mappings, SlackApiService(client) { sleeps += it })
            .resolve(listOf(participant))

        assertThat(result.users).containsEntry(participant.id, "U_PERSON")
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2))
        verify(client, times(2)).usersList(any<UsersListRequest>())
    }

    @Test
    fun `128 participants resolve from a complete paginated directory with two calls`() {
        val participants = (1..128).map {
            participant.copy(id = UUID.randomUUID(), navNoEmail = "participant$it@nav.no")
        }
        val users = participants.mapIndexed { index, person ->
            human().apply { id = "U_$index"; profile.email = person.navNoEmail }
        }
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersList(any<UsersListRequest>()))
            .thenReturn(page(users.take(64), "next"), page(users.drop(64)))

        val result = service.resolve(participants)

        assertThat(result.users).hasSize(128)
        assertThat(result.unresolvedParticipantIds).isEmpty()
        val requests = argumentCaptor<UsersListRequest>()
        verify(client, times(2)).usersList(requests.capture())
        assertThat(requests.allValues.map { it.cursor }).containsExactly("", "next")
        verify(client, never()).usersInfo(any<UsersInfoRequest>())
        verify(client, never()).usersLookupByEmail(any<UsersLookupByEmailRequest>())
    }

    @Test
    fun `failure on a later directory page never returns partial identities`() {
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersList(any<UsersListRequest>()))
            .thenReturn(page(listOf(human()), "next"), UsersListResponse().apply { error = "missing_scope" })
        assertThatThrownBy { service.resolve(listOf(participant)) }
            .isInstanceOf(SlackIntegrationException::class.java)
    }

    @Test
    fun `repeated directory cursors fail rather than looping indefinitely`() {
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersList(any<UsersListRequest>())).thenReturn(page(listOf(human()), "same"))
        assertThatThrownBy { service.resolve(listOf(participant)) }
            .isInstanceOf(SlackIntegrationException::class.java)
            .hasMessageContaining("repeated_cursor")
        verify(client, times(2)).usersList(any<UsersListRequest>())
    }

    @Test
    fun `ambiguous directory email remains unresolved`() {
        whenever(mappings.mappedParticipants()).thenReturn(emptyMap())
        whenever(client.usersList(any<UsersListRequest>()))
            .thenReturn(page(listOf(human(), human().apply { id = "U_OTHER" })))
        assertThat(service.resolve(listOf(participant)).unresolvedParticipantIds).containsExactly(participant.id)
    }

    private fun human() = User().apply {
        id = "U_PERSON"
        profile = User.Profile().apply { email = participant.navNoEmail }
    }

    private fun page(users: List<User>, cursor: String = "") = UsersListResponse().apply {
        isOk = true
        members = users
        responseMetadata = ResponseMetadata().apply { nextCursor = cursor }
    }
}
