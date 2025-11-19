package navikt.appsec.securitychampionstats.integration.slack

data class SlackConfig(
    val botToken: String,
    val trackedChannels: Set<String>,
    val activityLookbackHours: Long = 24
)

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