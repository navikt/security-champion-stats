package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest
import com.slack.api.methods.request.conversations.ConversationsInviteRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.element.ImageElement
import navikt.appsec.securitychampionapp.integrations.slack.dto.NewSecurityChampion
import navikt.appsec.securitychampionapp.integrations.slack.dto.RemovedSecurityChampion
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackActivitySummary
import navikt.appsec.securitychampionapp.integrations.slack.dto.UserInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Service
class SlackService(
    private val client: MethodsClient,
    @Value($$"${slack.playbook_url}") private val playbookUrl: String,
    @Value($$"${slack.sc-channel-id}") private val scChannelId: String,
    @Value($$"${slack.appsec-activity-channel-id") private  val appSecActivityChannelId: String,
) {

    private val clock = Clock.systemUTC()
    private val logger = LoggerFactory.getLogger(SlackService::class.java)

    private fun resolveUserIdByEmail(email: String): UserInfo {
        val result = withRateLimitRetry {
            client.usersLookupByEmail { user: UsersLookupByEmailRequest.UsersLookupByEmailRequestBuilder ->
                user.email(email)
            }
        }
        if (!result.isOk) return UserInfo("", "")
        return UserInfo(
            userId = result.user.id,
            fullname = result.user.realName ?: result.user.name,
            imageUrl = result.user.profile?.imageOriginal ?: result.user.profile?.image192,
        )
    }

    private fun countUserMessagesInChannel(userId: String, channelId: String): Int {
        val oldest = Instant.now(clock)
            .minus(24, ChronoUnit.HOURS)
            .epochSecond
            .toString()
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

    private fun inviteUserToChannel(channelId: String, userId: String, email: String): Boolean {
        val result = withRateLimitRetry {
            client.conversationsInvite { request: ConversationsInviteRequest.ConversationsInviteRequestBuilder ->
                request.channel(channelId)
                request.users(listOf(userId))
            }
        }

        if (result.isOk || result.error == "already_in_channel") {
            return true
        }

        logger.warn("Failed to invite user with email $email to channel $channelId, error: ${result.error}")
        return false
    }

    private fun postWelcomeMessage(channelId: String, champion: NewSecurityChampion, userInfo: UserInfo) {
        val fallbackText = buildWelcomeText(champion, userInfo.userId)
        val request = ChatPostMessageRequest.builder()
            .channel(channelId)
            .text(fallbackText)
            .blocks(buildWelcomeBlocks(champion, userInfo))
            .build()

        val result = withRateLimitRetry { client.chatPostMessage(request) }
        if (!result.isOk) {
            logger.warn(
                "Failed to post welcome message for ${champion.email} in channel $channelId, error: ${result.error}"
            )
        }
    }

    private fun buildWelcomeBlocks(champion: NewSecurityChampion, userInfo: UserInfo): List<LayoutBlock> {
        val announcement = SectionBlock.builder()
            .text(markdownText(buildAnnouncementText(champion, userInfo.userId)))

        userInfo.imageUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { imageUrl ->
                announcement.accessory(
                    ImageElement.builder()
                        .imageUrl(imageUrl)
                        .altText(champion.fullName)
                        .build()
                )
            }

        return listOf(
            announcement.build(),
            SectionBlock.builder()
                .text(markdownText(buildWelcomeBodyText()))
                .build()
        )
    }

    private fun buildWelcomeText(champion: NewSecurityChampion, userId: String): String {
        return listOf(
            buildAnnouncementText(champion, userId),
            buildWelcomeBodyText(),
        ).joinToString(separator = "\n\n")
    }

    private fun buildAnnouncementText(champion: NewSecurityChampion, userId: String): String {
        return "🎉 ${champion.teamNames} har fått seg en ny Security Champion!\n" +
            "${champion.fullName} (<@${userId}>)"
    }

    private fun buildWelcomeBodyText(): String {
        return "Velkommen! 😺 🛡️\nSjekk «${toSlackLink(playbookUrl, "Ny Security Champion")}» " +
            "for praktiske oppgaver å starte med 💪"
    }

    private fun toSlackLink(url: String, label: String): String {
        return if (url.isBlank()) label else "<$url|$label>"
    }

    fun getUserActivitySummaryByEmail(email: String, channelIds: List<String>): SlackActivitySummary {
        val userInfo = resolveUserIdByEmail(email)
        if (userInfo.userId.isBlank()) {
            logger.warn("User ID was blank then fetch data for user $email")
            return SlackActivitySummary(
                userInfo = UserInfo("", ""),
                inTrackedChannels = listOf(""),
                messagesPerChannel = emptyMap(),
                totalMessages = 0
            )
        }

        var totalMessages = 0
        val messagesPerChannel = mutableMapOf<String, Int>()
        for (channelId in channelIds) {
            val messageCount = countUserMessagesInChannel(userInfo.userId, channelId)
            if (messageCount > 0) {
                totalMessages += messageCount
                messagesPerChannel[channelId] = messageCount
            }
        }

        return SlackActivitySummary(
            userInfo = userInfo,
            inTrackedChannels = channelIds.filter { messagesPerChannel.containsKey(it) },
            messagesPerChannel = messagesPerChannel,
            totalMessages = totalMessages
        )
    }

    fun addSecurityChampionsToSlack(channelId: String = appSecActivityChannelId, champions: List<NewSecurityChampion>) {
        champions
            .distinctBy { it.email.lowercase() }
            .forEach { champion ->
                val userInfo = resolveUserIdByEmail(champion.email)
                if (userInfo.userId.isBlank()) {
                    logger.warn("Unable to find Slack user for ${champion.email}")
                    return@forEach
                }

                if (inviteUserToChannel(channelId, userInfo.userId, champion.email)) {
                    postWelcomeMessage(channelId, champion, userInfo)
                }
            }
    }

    private fun postRemovalMessage(channelId: String, champion: RemovedSecurityChampion, userInfo: UserInfo) {
        val request = ChatPostMessageRequest.builder()
            .channel(channelId)
            .text(buildRemovalText(champion, userInfo))
            .blocks(buildRemovalBlocks(champion, userInfo))
            .build()

        val result = withRateLimitRetry { client.chatPostMessage(request) }
        if (!result.isOk) {
            logger.warn(
                "Failed to post removal message for ${champion.email} in channel $channelId, error: ${result.error}"
            )
        }
    }

    private fun buildRemovalBlocks(champion: RemovedSecurityChampion, userInfo: UserInfo): List<LayoutBlock> {
        val mention = userInfo.userId.takeIf { it.isNotBlank() }
            ?.let { "<@$it>" }
            ?: champion.fullName

        val removal = SectionBlock.builder()
            .text(markdownText("💀 Security Champion fjernet fra ${champion.teamName}\n$mention"))

        userInfo.imageUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { imageUrl ->
                removal.accessory(
                    ImageElement.builder()
                        .imageUrl(imageUrl)
                        .altText(champion.fullName)
                        .build()
                )
            }

        return listOf(removal.build())
    }

    private fun buildRemovalText(champion: RemovedSecurityChampion, userInfo: UserInfo): String {
        val mention = userInfo.userId.takeIf { it.isNotBlank() }
            ?.let { "<@$it>" }
            ?: champion.fullName

        return "💀 Security Champion fjernet fra ${champion.teamName}\n$mention"
    }

    fun announceSecurityChampionsRemovedFromSlack(channelId: String = appSecActivityChannelId, champions: List<RemovedSecurityChampion>) {
        champions
            .distinctBy { it.email.lowercase() }
            .forEach { champion ->
                val userInfo = resolveUserIdByEmail(champion.email)
                postRemovalMessage(channelId, champion, userInfo)
            }
    }
}
