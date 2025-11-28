package navikt.appsec.securitychampionstats.integration.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.users.UsersConversationsRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import navikt.appsec.securitychampionstats.integration.slack.dto.SlackActivitySummary
import navikt.appsec.securitychampionstats.integration.slack.dto.UserInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit
import java.time.Instant
import kotlin.math.max

@Service
class SlackService(
    @Value("\${slack.channelId}") private val channel: String,
    val client: MethodsClient
) {

    private val logger = LoggerFactory.getLogger(SlackService::class.java)

    private fun resolveUserIdByEmail(email: String): UserInfo {
        val result = client.usersLookupByEmail { user: UsersLookupByEmailRequest.UsersLookupByEmailRequestBuilder ->
            user.email(email)
        }
        if (!result.isOk) return UserInfo("", "")
        return UserInfo(result.user.id, result.user.name)
    }

    private fun userConversationIds(userId: String): Set<String> {
        val userIDs = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val result = client.usersConversations { conversation: UsersConversationsRequest.UsersConversationsRequestBuilder ->
                conversation.user(userId).limit(200).cursor(cursor)
            }
            if (!result.isOk) break
            result.channels.forEach { userIDs += it.id }
            cursor = result.responseMetadata.nextCursor.takeIf { it.isNotBlank() }
        } while (cursor != null)
        return userIDs
    }

    private fun countUserMessagesInChannel(userId: String, channelId: String): Int {
        val oldest = Instant.now().minus(24L, ChronoUnit.HOURS).epochSecond.toString()
        var cursor: String? = null
        var count = 0
        do {
            val request = ConversationsHistoryRequest.builder()
                .channel(channelId)
                .oldest(oldest)
                .limit(200)
                .cursor(cursor)
                .build()
            val result = withRateLimitRetry { client.conversationsHistory(request) }
            if (!result.isOk) break
            result.messages.forEach { message ->
                if (message.user == userId && message.subtype == null) count++
            }
            cursor = result.responseMetadata.nextCursor.takeIf { it.isNotBlank() }
        } while (cursor != null)
        return count
    }

    private fun <T> withRateLimitRetry(block: () -> T): T {
        var attempts = 0
        while (true) {
            try {
                return block()
            } catch (e: SlackApiException) {
                if (e.response.code == 429) {
                    val waitSec = max(e.response.headers["Retry-After"]?.firstOrNull()?.code?.toLong() ?: 1L, 1L)
                    Thread.sleep(waitSec * 1000)
                } else {
                    logger.error(e.message, e)
                    throw e
                }
            }
            attempts++
            if (attempts > 10) throw RuntimeException("Too many retries exceeded")
        }
    }
    fun summarizeActivity(email: String): SlackActivitySummary {
        val userInfo = resolveUserIdByEmail(email)
        val memberChannels = userConversationIds(userInfo.userId) // TODO this will be used later
        val amountPerChannel = countUserMessagesInChannel(userInfo.userId, channel)
        return SlackActivitySummary(
            userInfo = userInfo,
            inTrackedChannels = setOf(channel),
            messagesPerChannel = mapOf(Pair(channel, amountPerChannel)),
            totalMessages = amountPerChannel
        )
    }

}


