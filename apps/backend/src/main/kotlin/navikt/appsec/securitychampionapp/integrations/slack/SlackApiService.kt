package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest
import com.slack.api.methods.request.users.UsersInfoRequest
import com.slack.api.model.Message
import navikt.appsec.securitychampionapp.app.scoring.SlackActivityMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private const val MAX_RATE_LIMIT_RETRIES = 3
private val DEFAULT_RETRY_AFTER: Duration = Duration.ofSeconds(1)
private val MAX_RETRY_AFTER: Duration = Duration.ofSeconds(60)
private val THREAD_FETCH_DELAY: Duration = Duration.ofMillis(1200)

@Service
class SlackApiService(
    private val client: MethodsClient,
    private val sleeper: (Duration) -> Unit = { Thread.sleep(it) },
) {
    private val log = LoggerFactory.getLogger(SlackApiService::class.java)

    fun fetchScoringMessages(
        channelId: String,
        oldest: Instant,
        latest: Instant,
    ): List<SlackActivityMessage> {
        val messages = mutableListOf<SlackActivityMessage>()
        var cursor: String? = null
        do {
            val request = ConversationsHistoryRequest.builder()
                .channel(channelId)
                .oldest(oldest.toSlackTimestamp())
                .latest(latest.toSlackTimestamp())
                .limit(200)
                .cursor(cursor)
                .build()
            val response = call(SlackIntegrationException.Operation.HISTORY) { client.conversationsHistory(request) }
            if (response == null || !response.isOk) {
                throw SlackIntegrationException(SlackIntegrationException.Operation.HISTORY, response?.error)
            }

            val roots = response.messages ?: emptyList()
            roots.forEach { root ->
                messages += root.toActivityMessage(channelId)
                if (root.hasRepliesSince(oldest)) {
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
            sleeper(THREAD_FETCH_DELAY)
            val response = call(SlackIntegrationException.Operation.REPLIES) { client.conversationsReplies(request) }
            if (response == null || !response.isOk) {
                throw SlackIntegrationException(SlackIntegrationException.Operation.REPLIES, response?.error)
            }

            response.messages.orEmpty()
                .filter { it.ts != root.ts }
                .forEach { messages += it.toActivityMessage(channelId) }
            cursor = response.responseMetadata?.nextCursor?.takeIf { it.isNotBlank() }
        } while (cursor != null)
        return messages
    }

    fun fetchUserEmail(userId: String): String? {
        val request = UsersInfoRequest.builder().user(userId).build()
        val response = call(SlackIntegrationException.Operation.USERS_INFO) { client.usersInfo(request) }
        if (response?.error == "user_not_found") return null
        if (response == null || !response.isOk) {
            throw SlackIntegrationException(SlackIntegrationException.Operation.USERS_INFO, response?.error)
        }
        return response.user?.profile?.email?.takeIf { it.isNotBlank() }
    }

    internal fun <T> call(operation: SlackIntegrationException.Operation, request: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return request()
            } catch (e: SlackApiException) {
                if (e.response?.code != 429) throw SlackIntegrationException(operation, e.error?.error)
                if (attempt >= MAX_RATE_LIMIT_RETRIES) throw SlackIntegrationException(operation, "ratelimited")
                attempt++
                val retryAfter = e.retryAfter()
                log.info("Slack {} rate limited, retrying in {}s", operation.apiMethod, retryAfter.seconds)
                sleeper(retryAfter)
            } catch (_: IOException) {
                throw SlackIntegrationException(operation, "network_error")
            }
        }
    }

    private fun SlackApiException.retryAfter(): Duration =
        response?.header("Retry-After")?.toLongOrNull()
            ?.let { Duration.ofSeconds(it) }
            ?.coerceIn(DEFAULT_RETRY_AFTER, MAX_RETRY_AFTER)
            ?: DEFAULT_RETRY_AFTER

    private fun Message.hasRepliesSince(oldest: Instant): Boolean {
        if ((replyCount ?: 0) == 0 || ts.isNullOrBlank()) return false
        val lastReply = latestReply?.takeIf { it.isNotBlank() } ?: return true
        return !lastReply.toSlackInstant().isBefore(oldest)
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
        "$epochSecond.${(nano / 1000).toString().padStart(6, '0')}"
}
