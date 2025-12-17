package navikt.appsec.securitychampionstats.integrations

import com.slack.api.RequestConfigurator
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.methods.response.users.UsersLookupByEmailResponse
import com.slack.api.model.User
import navikt.appsec.securitychampionstats.integration.slack.SlackService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@SpringBootTest(
    classes = [SlackService::class],   // only load this bean + minimal Boot infra
)
@ActiveProfiles("test")
class SlackServiceTest {
    @MockitoBean lateinit var client: MethodsClient

    @Test
    fun `Test slack service if correct amount of message is return and collection of activity is correct`() {
        val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")

        val slackService = SlackService(
            channel = "test-channel",
            client = client,
            clock = Clock.fixed(
                fixedInstant,
                ZoneOffset.UTC
            )
        )

        val mockEmailResponse = UsersLookupByEmailResponse().apply {
            isOk = true
            user = User().apply {
                id = "test-id"
                name = "test-name"
            }
        }

        doReturn(mockEmailResponse)
            .whenever(client)
            .usersLookupByEmail(any<RequestConfigurator<UsersLookupByEmailRequest.UsersLookupByEmailRequestBuilder>>())

        val mockHistoryResponse = com.slack.api.methods.response.conversations.ConversationsHistoryResponse().apply {
            isOk = true
            messages = listOf(
                com.slack.api.model.Message().apply { user = "test-id"; ts = Instant.now().epochSecond.toString() },
                com.slack.api.model.Message().apply { user = "other-id"; ts = Instant.now().epochSecond.toString() },
                com.slack.api.model.Message().apply { user = "test-id"; ts = Instant.now().epochSecond.toString() }
            )
            responseMetadata = com.slack.api.model.ResponseMetadata().apply {
                nextCursor = ""
            }
        }

        doReturn(mockHistoryResponse)
            .whenever(client)
            .conversationsHistory(any<ConversationsHistoryRequest>())

        val activitySummary = slackService.summarizeActivity("test-email")
        assert(activitySummary.totalMessages == 2)
        assert(activitySummary.userInfo.fullname == "test-name")
        assert(activitySummary.userInfo.userId == "test-id")
    }
}