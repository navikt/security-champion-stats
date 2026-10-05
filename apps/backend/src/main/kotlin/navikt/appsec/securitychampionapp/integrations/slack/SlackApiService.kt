package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest
import com.slack.api.model.Message
import navikt.appsec.securitychampionapp.app.scoring.SlackActivityMessage
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class SlackApiService(
    private val client: MethodsClient,
) {
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
            val response = client.conversationsHistory(request)
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
            val response = client.conversationsReplies(request)
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
}
