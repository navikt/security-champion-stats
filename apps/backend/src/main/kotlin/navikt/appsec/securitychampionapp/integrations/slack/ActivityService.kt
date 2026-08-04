package navikt.appsec.securitychampionapp.integrations.slack

import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackActivitySummary
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackActivitySummaryResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ActivityService(
    private val slackApiService: SlackApiService,
    @Value($$"${slack.appsec-activity-channel-id}") private val scChannelId: String,
    @Value($$"${pointsSystem.amount.message}") private val pointsForMessage: Int,
) {
    private val logger = LoggerFactory.getLogger(ActivityService::class.java)

    fun calculateActivityForMembers(securityChampions: List<String>): SlackActivitySummaryResponse {
        val userData = securityChampions.mapNotNull { email ->
            slackApiService.fetchUserIdByEmail(email)
        }

        if (userData.isEmpty() || userData.size != securityChampions.size) {
            logger.warn("Failed to fetch all users from slack for point calculation")
            return SlackActivitySummaryResponse(
                isOk = false,
                slackActivitySummaries = emptyList(),
                error = "Failed to fetch all users from slack for point calculation"
            )
        }

        val conversationHistory = slackApiService.fetchChannelConversation(scChannelId)

        val slackActivitySummaries = mutableListOf<SlackActivitySummary>()
        var totalPoints = 0
        userData.forEach { user ->
            conversationHistory.forEach { history ->
                val messages = history.messages.filter { message -> message.user == user.user.id }
                totalPoints += messages.size * pointsForMessage
            }

            slackActivitySummaries.add(
                SlackActivitySummary(
                    email = user.user.profile.email,
                    totalPoints = totalPoints
                )
            )
        }

        return SlackActivitySummaryResponse(
            slackActivitySummaries,
            isOk = true
        )
    }


}