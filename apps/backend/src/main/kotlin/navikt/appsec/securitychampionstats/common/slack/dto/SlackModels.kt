package navikt.appsec.securitychampionstats.common.slack.dto

data class SlackActivitySummary(
    val userInfo: UserInfo,
    val inTrackedChannels: Set<String>,
    val messagesPerChannel: Map<String, Int>,
    val totalMessages: Int,
)

data class UserInfo(
    val userId: String,
    val fullname: String,
)