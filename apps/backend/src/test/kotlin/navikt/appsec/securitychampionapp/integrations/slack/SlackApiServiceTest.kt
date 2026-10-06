package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse
import com.slack.api.model.Message
import com.slack.api.model.ResponseMetadata
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

class SlackApiServiceTest {
    private val client = mock<MethodsClient>()
    private val service = SlackApiService(client)
    private val latest = Instant.parse("2026-10-06T00:00:00.123456789Z")

    @Test
    fun `should fetch root messages and thread replies for scoring`() {
        val root = message("1791187200.000001", "U_ROOT").apply { replyCount = 1 }
        val reply = message("1791187260.000001", "U_REPLY").apply { threadTs = root.ts }
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>()))
            .thenReturn(history(listOf(root)))
        whenever(client.conversationsReplies(any<ConversationsRepliesRequest>()))
            .thenReturn(replies(listOf(root, reply)))

        val messages = service.fetchScoringMessages("scoring-channel", latest)

        assertThat(messages.map { it.userId }).containsExactly("U_ROOT", "U_REPLY")
        assertThat(messages).allSatisfy { assertThat(it.channelId).isEqualTo("scoring-channel") }
        val historyRequest = argumentCaptor<ConversationsHistoryRequest>()
        verify(client).conversationsHistory(historyRequest.capture())
        assertThat(historyRequest.firstValue.channel).isEqualTo("scoring-channel")
        assertThat(historyRequest.firstValue.latest).isEqualTo("1791244800.123456789")
        assertThat(historyRequest.firstValue.limit).isEqualTo(200)
        val replyRequest = argumentCaptor<ConversationsRepliesRequest>()
        verify(client).conversationsReplies(replyRequest.capture())
        assertThat(replyRequest.firstValue.channel).isEqualTo("scoring-channel")
        assertThat(replyRequest.firstValue.ts).isEqualTo(root.ts)
        assertThat(replyRequest.firstValue.limit).isEqualTo(200)
    }

    @Test
    fun `should paginate channel history and thread replies without duplicating thread roots`() {
        val root = message("1791187200.000001", "U_ROOT").apply { replyCount = 2 }
        val firstReply = message("1791187260.000001", "U_FIRST")
        val secondReply = message("1791187320.000001", "U_SECOND")
        val nextRoot = message("1791187380.000001", "U_NEXT")
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>()))
            .thenReturn(history(listOf(root), "history-next"), history(listOf(nextRoot), " "))
        whenever(client.conversationsReplies(any<ConversationsRepliesRequest>()))
            .thenReturn(replies(listOf(root, firstReply), "replies-next"), replies(listOf(secondReply), ""))

        val messages = service.fetchScoringMessages("channel", latest)

        assertThat(messages.map { it.userId }).containsExactly("U_ROOT", "U_FIRST", "U_SECOND", "U_NEXT")
        val historyRequests = argumentCaptor<ConversationsHistoryRequest>()
        verify(client, times(2)).conversationsHistory(historyRequests.capture())
        assertThat(historyRequests.allValues.map { it.cursor }).containsExactly(null, "history-next")
        assertThat(historyRequests.allValues).allSatisfy {
            assertThat(it.channel).isEqualTo("channel")
            assertThat(it.latest).isEqualTo("1791244800.123456789")
        }
        val replyRequests = argumentCaptor<ConversationsRepliesRequest>()
        verify(client, times(2)).conversationsReplies(replyRequests.capture())
        assertThat(replyRequests.allValues.map { it.cursor }).containsExactly(null, "replies-next")
        assertThat(replyRequests.allValues).allSatisfy {
            assertThat(it.channel).isEqualTo("channel")
            assertThat(it.ts).isEqualTo(root.ts)
        }
    }

    @Test
    fun `should preserve timestamps and scoring fields without fetching nonexistent replies`() {
        val root = message("1791187200.123456", "U_ROOT").apply {
            edited = Message.Edited().apply { ts = "1791187260.000001" }
            subtype = "file_share"
            botId = "B123"
        }
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>()))
            .thenReturn(history(listOf(root)))

        val result = service.fetchScoringMessages("channel", latest).single()

        assertThat(result.timestamp).isEqualTo(Instant.parse("2026-10-05T08:00:00.123456Z"))
        assertThat(result.editedAt).isEqualTo(Instant.parse("2026-10-05T08:01:00.000001Z"))
        assertThat(result.text).isEqualTo(root.text)
        assertThat(result.subtype).isEqualTo("file_share")
        assertThat(result.botId).isEqualTo("B123")
        verify(client, never()).conversationsReplies(any<ConversationsRepliesRequest>())
    }

    @Test
    fun `should return no messages when channel history is empty`() {
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>())).thenReturn(history(null))

        assertThat(service.fetchScoringMessages("channel", latest)).isEmpty()

        verify(client, never()).conversationsReplies(any<ConversationsRepliesRequest>())
    }

    @Test
    fun `should fail rather than return partial history when a later page fails`() {
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>())).thenReturn(
            history(listOf(message("1791187200.000001", "U_ROOT")), "next"),
            ConversationsHistoryResponse().apply {
                isOk = false
                error = "not_in_channel"
            },
        )

        assertThatThrownBy { service.fetchScoringMessages("channel", latest) }
            .isInstanceOf(SlackIntegrationException::class.java)
            .hasMessage("Slack conversations.history failed: not_in_channel; invite the app to the configured channel")
    }

    @Test
    fun `should fail rather than return partial thread replies when a later page fails`() {
        val root = message("1791187200.000001", "U_ROOT").apply { replyCount = 2 }
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>())).thenReturn(history(listOf(root)))
        whenever(client.conversationsReplies(any<ConversationsRepliesRequest>())).thenReturn(
            replies(listOf(root, message("1791187260.000001", "U_REPLY")), "next"),
            ConversationsRepliesResponse().apply {
                isOk = false
                error = "missing_scope"
            },
        )

        assertThatThrownBy { service.fetchScoringMessages("channel", latest) }
            .isInstanceOf(SlackIntegrationException::class.java)
            .hasMessageContaining("missing_scope")
    }

    @Test
    fun `should preserve an unfamiliar error field without including the full Slack response`() {
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>())).thenReturn(
            ConversationsHistoryResponse().apply {
                isOk = false
                error = "request_timeout"
                messages = listOf(message("1791187200.000001", "U_ROOT"))
            },
        )

        assertThatThrownBy { service.fetchScoringMessages("channel", latest) }
            .isInstanceOf(SlackIntegrationException::class.java)
            .hasMessage("Slack conversations.history failed: request_timeout; check Slack availability and API access")
    }

    @Test
    fun `should fail when Slack returns no history response`() {
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>())).thenReturn(null)

        assertThatThrownBy { service.fetchScoringMessages("channel", latest) }
            .isInstanceOf(SlackIntegrationException::class.java)
            .hasMessageContaining("no response")
    }

    @Test
    fun `should fail when Slack returns no thread response`() {
        val root = message("1791187200.000001", "U_ROOT").apply { replyCount = 1 }
        whenever(client.conversationsHistory(any<ConversationsHistoryRequest>())).thenReturn(history(listOf(root)))
        whenever(client.conversationsReplies(any<ConversationsRepliesRequest>())).thenReturn(null)

        assertThatThrownBy { service.fetchScoringMessages("channel", latest) }
            .isInstanceOf(SlackIntegrationException::class.java)
            .hasMessageContaining("no response")
    }

    private fun message(timestamp: String, author: String): Message = Message().apply {
        ts = timestamp
        user = author
        text = "A qualifying message with enough text"
    }

    private fun history(messages: List<Message>?, cursor: String? = null): ConversationsHistoryResponse =
        ConversationsHistoryResponse().apply {
            isOk = true
            this.messages = messages
            responseMetadata = cursor?.let { ResponseMetadata().apply { nextCursor = it } }
        }

    private fun replies(messages: List<Message>, cursor: String? = null): ConversationsRepliesResponse =
        ConversationsRepliesResponse().apply {
            isOk = true
            this.messages = messages
            responseMetadata = cursor?.let { ResponseMetadata().apply { nextCursor = it } }
        }
}
