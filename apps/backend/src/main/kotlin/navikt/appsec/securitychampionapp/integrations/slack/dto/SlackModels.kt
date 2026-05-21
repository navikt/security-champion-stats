package navikt.appsec.securitychampionapp.integrations.slack.dto

data class SlackActivitySummary(
    val userInfo: UserInfo,
    val inTrackedChannels: List<String>,
    val messagesPerChannel: Map<String, Int>,
    val totalMessages: Int,
)

data class UserInfo(
    val userId: String,
    val fullname: String,
)