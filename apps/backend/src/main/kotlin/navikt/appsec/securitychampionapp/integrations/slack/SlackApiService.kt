package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.methods.response.chat.ChatPostMessageResponse
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersUpdateResponse
import com.slack.api.methods.response.users.UsersLookupByEmailResponse
import com.slack.api.model.Message
import com.slack.api.model.User
import navikt.appsec.securitychampionapp.app.scoring.SlackActivityMessage
import navikt.appsec.securitychampionapp.integrations.slack.dto.SecurityChampionMessage
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackCommonResponse
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackUserResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.math.BigDecimal
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

    fun fetchScoringMessages(
        channelId: String,
        latest: Instant,
    ): List<SlackActivityMessage> {
        val messages = mutableListOf<SlackActivityMessage>()
        var cursor: String? = null
        do {
            val request = ConversationsHistoryRequest.builder()
                .channel(channelId)
                .latest(latest.toSlackTimestamp())
                .limit(200)
                .cursor(cursor)
                .build()
            val response = if (mockResponse.useMockResponses()) {
                mockResponse.fetchMockData(
                    "classpath:mock/slack/slack_conversations_history.json",
                    ConversationsHistoryResponse::class.java,
                )
            } else {
                client.conversationsHistory(request)
            } as ConversationsHistoryResponse?
            if (response == null || !response.isOk) {
                throw SlackIntegrationException("Failed to fetch Slack channel history: ${response?.error ?: "no response"}")
            }

            val roots = response.messages ?: emptyList()
            roots.forEach { root ->
                messages += root.toActivityMessage(channelId)
                if ((root.replyCount ?: 0) > 0 && !root.ts.isNullOrBlank()) {
                    messages += fetchThreadReplies(channelId, root)
                }
            }
            cursor = response.responseMetadata?.nextCursor?.takeIf { it.isNotBlank() }
        } while (cursor != null)
        return messages
    }

    private fun fetchThreadReplies(
        channelId: String,
        root: Message,
    ): List<SlackActivityMessage> {
        val messages = mutableListOf<SlackActivityMessage>()
        var cursor: String? = null
        do {
            val request = ConversationsRepliesRequest.builder()
                .channel(channelId)
                .ts(root.ts)
                .limit(200)
                .cursor(cursor)
                .build()
            val response = if (mockResponse.useMockResponses()) {
                mockResponse.fetchMockData(
                    "classpath:mock/slack/slack_conversations_replies.json",
                    ConversationsRepliesResponse::class.java,
                )
            } else {
                client.conversationsReplies(request)
            } as ConversationsRepliesResponse?
            if (response == null || !response.isOk) {
                throw SlackIntegrationException("Failed to fetch Slack thread replies: ${response?.error ?: "no response"}")
            }

            response.messages.orEmpty()
                .filter { it.ts != root.ts }
                .forEach { messages += it.toActivityMessage(channelId) }
            cursor = response.responseMetadata?.nextCursor?.takeIf { it.isNotBlank() }
        } while (cursor != null)
        return messages
    }

    private fun Message.toActivityMessage(channelId: String): SlackActivityMessage =
        SlackActivityMessage(
            channelId = channelId,
            userId = user,
            text = text,
            timestamp = ts.toSlackInstant(),
            editedAt = edited?.ts?.toSlackInstant(),
            subtype = subtype,
            botId = botId,
        )

    private fun String.toSlackInstant(): Instant {
        val value = toBigDecimal()
        val seconds = value.toLong()
        val nanos = value.subtract(BigDecimal.valueOf(seconds)).movePointRight(9).toLong()
        return Instant.ofEpochSecond(seconds, nanos)
    }

    private fun Instant.toSlackTimestamp(): String =
        "$epochSecond.${nano.toString().padStart(9, '0')}"

    fun fetchUserIdByEmail(email: String): UsersLookupByEmailResponse? {
        return if (mockResponse.useMockResponses()) {
            mockResponse.fetchMockData("classpath:mock/slack/slack_fetch_user.json", UsersLookupByEmailResponse::class.java)
        } else {
            client.usersLookupByEmail { user: UsersLookupByEmailRequest.UsersLookupByEmailRequestBuilder ->
                user.email(email)
            }
        } as UsersLookupByEmailResponse?
    }


    fun updateUsersGroup(userIds: List<String>, userGroupId: String): SlackCommonResponse {
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

        return SlackCommonResponse(
            isOk = response?.isOk ?: false,
            error = response?.error ?: "Unknown error"
        )
    }

    fun postChatMessage(channelId: String, securityChampionMessage: SecurityChampionMessage): SlackCommonResponse {
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

        return SlackCommonResponse(
            isOk = response?.isOk ?: false,
            error = response?.error ?: "Unknown error"
        )
    }

    fun fetchAllUsers(cursor: String?, level: Int = 0): SlackUserResponse {
        if (level >= 20) {
            return SlackUserResponse(
                isOk = false,
                users = emptyList(),
                error = "Max level reached, stopping fetching users"
            )
        }

        val response = client.usersList {
            it.cursor(cursor)
        }

        if (!response.isOk) {
            return SlackUserResponse(
                isOk = false,
                users = emptyList(),
                error = response.error
            )
        }

        val members = response.members ?: emptyList()
        val nextCursor = response.responseMetadata?.nextCursor?.takeIf { it.isNotBlank() } ?: return SlackUserResponse(
            isOk = true,
            users = members,
            error = null
        )

        val nextUsers = fetchAllUsers(nextCursor, level + 1)
        return SlackUserResponse(
            isOk = true,
            users = members + nextUsers.users,
            error = nextUsers.error
        )
    }
}
