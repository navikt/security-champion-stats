package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersUpdateResponse
import com.slack.api.methods.response.users.UsersLookupByEmailResponse
import navikt.appsec.securitychampionapp.integrations.slack.dto.SecurityChampionMessage
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class SlackApiService(
    private val client: MethodsClient,
    private val mockResponse: SlackResponseMock,
    @Value($$"${slack.playbook_url}") private val playbookUrl: String,
    @Value($$"${slack.sc-channel-id}") private val scChannelId: String,
    @Value($$"${slack.appsec-activity-channel-id}") private  val appSecActivityChannelId: String,
) {

    private val clock = Clock.systemUTC()
    private val logger = LoggerFactory.getLogger(SlackApiService::class.java)


    fun fetchChannelConversation(channelId: String): List<ConversationsHistoryResponse> {
        val oldest = Instant.now(clock)
            .minus(23, ChronoUnit.HOURS)
            .epochSecond
            .toString()

        var cursor: String? = null
        val messages = emptyList<ConversationsHistoryResponse>().toMutableList()
        do {
            val request = ConversationsHistoryRequest.builder()
                .channel(channelId)
                .oldest(oldest)
                .limit(200)
                .cursor(cursor)
                .build()
            val result = if (mockResponse.useMockResponses()) {
                mockResponse.fetchMockData(
                    "classpath:mock/slack/slack_conversations_history.json",
                    ConversationsHistoryResponse::class.java
                )
            } else {
                client.conversationsHistory(request)
            } as ConversationsHistoryResponse?

            if (result == null || !result.isOk) {
                logger.warn("Failed getting conversation history, with error: ${result?.error}")
                return messages
            }
            messages.add(result)
            cursor = result.responseMetadata.nextCursor.takeIf { it.isNotBlank() }
        } while (cursor != null)

        return messages
    }

    fun fetchUserIdByEmail(email: String): UsersLookupByEmailResponse? {
        return if (mockResponse.useMockResponses()) {
            mockResponse.fetchMockData("classpath:mock/slack/slack_fetch_user.json", UsersLookupByEmailResponse::class.java)
        } else {
            client.usersLookupByEmail { user: UsersLookupByEmailRequest.UsersLookupByEmailRequestBuilder ->
                user.email(email)
            }
        } as UsersLookupByEmailResponse?
    }


    fun updateUsersGroup(userIds: List<String>, userGroupId: String): SlackResponse {
        val response = if (mockResponse.useMockResponses()) {
            mockResponse.fetchMockData(
                "classpath:mock/slack/slack_usergroups_users_update.json",
                UsergroupsUsersUpdateResponse::class.java
            )
        } else {
            client.usergroupsUsersUpdate {
                it.usergroup(userGroupId)
                    .users(userIds)
            }
        } as UsergroupsUsersUpdateResponse?

        if (response == null || !response.isOk) {
            logger.warn("Failed updating user group $userGroupId, with error: ${response?.error}")
        }

        return SlackResponse(
            isOk = response?.isOk ?: false,
            error = response?.error ?: "Unknown error"
        )
    }

    fun postChatMessage(channelId: String, securityChampionMessage: SecurityChampionMessage): SlackResponse {
        val response = if (mockResponse.useMockResponses()) {
            mockResponse.fetchMockData(
                "classpath:mock/slack/slack_post_message.json",
                ChatPostMessageResponse::class.java
            )
        } else {
            client.chatPostMessage {
                it.channel(channelId)
                    .text(securityChampionMessage.fallBackBlock)
                    .blocks(securityChampionMessage.messageBlock)
            }
        } as ChatPostMessageResponse?

        if (response == null || !response.isOk) {
            logger.warn("Failed posting message to channel $channelId, with error: ${response?.error}")
        }

        return SlackResponse(
            isOk = response?.isOk ?: false,
            error = response?.error ?: "Unknown error"
        )
    }
}
