package navikt.appsec.securitychampionapp.integrations.slack.dto

data class SlackActivitySummary(
    val userInfo: UserInfo,
    val inTrackedChannels: List<String>,
    val messagesPerChannel: Map<String, Int>,
    val totalMessages: Int,
    val error: String? = null
)

data class UserInfo(
    val userId: String,
    val fullname: String,
    val imageUrl: String? = null,
    val error: String? = null
)