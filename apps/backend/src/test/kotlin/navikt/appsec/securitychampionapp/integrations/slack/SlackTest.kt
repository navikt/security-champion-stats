package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.RequestConfigurator
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersUpdateRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersUpdateResponse
import com.slack.api.methods.response.users.UsersLookupByEmailResponse
import com.slack.api.model.User
import navikt.appsec.securitychampionapp.integrations.slack.dto.SecurityChampionMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader
import tools.jackson.databind.ObjectMapper

class SlackTest {
    private val environment = StandardEnvironment()
    private val objectMapper = ObjectMapper()
    private val resourceLoader = DefaultResourceLoader()
    private val client = mock<MethodsClient>()
    private val mockResponse = SlackResponseMock(resourceLoader, objectMapper, environment)

    private val slackService = SlackApiService(
        client = client,
        mockResponse = mockResponse,
        playbookUrl = "https://sikkerhet.nav.no/docs/ny-security-champion/",
        scChannelId = "sc-channel-id",
        appSecActivityChannelId = "appsec-channel-id",
    )

    @Test
    fun `should fetch user by email`() {
        whenever(client.usersLookupByEmail(any<RequestConfigurator<UsersLookupByEmailRequest.UsersLookupByEmailRequestBuilder>>()))
            .thenReturn(userLookupResponse())

        val response = slackService.fetchUserIdByEmail("ada.lovelace@nav.no")

        val lookupRequest = capturedUserLookupRequest()
        assertThat(lookupRequest.email).isEqualTo("ada.lovelace@nav.no")
        assertThat(response?.user?.id).isEqualTo("U08FGDKU83D")
    }

    @Test
    fun `should update user group`() {
        whenever(client.usergroupsUsersUpdate(any<RequestConfigurator<UsergroupsUsersUpdateRequest.UsergroupsUsersUpdateRequestBuilder>>()))
            .thenReturn(userGroupUpdateResponse())

        val response = slackService.updateUsersGroup(listOf("U08FGDKU83D"), "user-group-id")

        val request = capturedUserGroupUpdateRequest()
        assertThat(request.usergroup).isEqualTo("user-group-id")
        assertThat(request.users).containsExactly("U08FGDKU83D")
        assertThat(response.isOk).isTrue()
    }

    @Test
    fun `should post chat message`() {
        whenever(client.chatPostMessage(any<RequestConfigurator<ChatPostMessageRequest.ChatPostMessageRequestBuilder>>()))
            .thenReturn(chatPostMessageResponse())

        val response = slackService.postChatMessage(
            "channel-id",
            SecurityChampionMessage(
                fallBackBlock = "Fallback text",
                messageBlock = mutableListOf()
            )
        )

        val request = capturedChatPostMessageRequest()
        assertThat(request.channel).isEqualTo("channel-id")
        assertThat(request.text).isEqualTo("Fallback text")
        assertThat(response.isOk).isTrue()
    }

    private fun capturedUserLookupRequest(): UsersLookupByEmailRequest {
        val requestCaptor =
            argumentCaptor<RequestConfigurator<UsersLookupByEmailRequest.UsersLookupByEmailRequestBuilder>>()
        verify(client, times(1)).usersLookupByEmail(requestCaptor.capture())

        return requestCaptor.firstValue
            .configure(UsersLookupByEmailRequest.builder())
            .build()
    }

    private fun capturedUserGroupUpdateRequest(): UsergroupsUsersUpdateRequest {
        val requestCaptor =
            argumentCaptor<RequestConfigurator<UsergroupsUsersUpdateRequest.UsergroupsUsersUpdateRequestBuilder>>()
        verify(client, times(1)).usergroupsUsersUpdate(requestCaptor.capture())

        return requestCaptor.firstValue
            .configure(UsergroupsUsersUpdateRequest.builder())
            .build()
    }

    private fun capturedChatPostMessageRequest(): ChatPostMessageRequest {
        val requestCaptor =
            argumentCaptor<RequestConfigurator<ChatPostMessageRequest.ChatPostMessageRequestBuilder>>()
        verify(client, times(1)).chatPostMessage(requestCaptor.capture())

        return requestCaptor.firstValue
            .configure(ChatPostMessageRequest.builder())
            .build()
    }

    private fun userLookupResponse(): UsersLookupByEmailResponse {
        val profile = User.Profile().apply {
            email = "ada.lovelace@nav.no"
        }
        val slackUser = User().apply {
            id = "U08FGDKU83D"
            name = "ada.lovelace"
            realName = "Ada Lovelace"
            this.profile = profile
        }
        return UsersLookupByEmailResponse().apply {
            isOk = true
            user = slackUser
        }
    }

    private fun userGroupUpdateResponse(): UsergroupsUsersUpdateResponse = UsergroupsUsersUpdateResponse().apply {
        isOk = true
    }

    private fun chatPostMessageResponse(): ChatPostMessageResponse = ChatPostMessageResponse().apply {
        isOk = true
    }
}
