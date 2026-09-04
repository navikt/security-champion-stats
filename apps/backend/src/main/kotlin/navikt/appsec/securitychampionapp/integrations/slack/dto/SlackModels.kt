package navikt.appsec.securitychampionapp.integrations.slack.dto

import com.slack.api.model.User
import com.slack.api.model.block.LayoutBlock

data class SlackActivitySummary(
    val email: String,
    val totalPoints: Int,
    val error: String? = null
)

data class SlackActivitySummaryResponse(
    val slackActivitySummaries: List<SlackActivitySummary>,
    val isOk: Boolean,
    val error: String? = null
)

data class SecurityChampion(
    val email: String,
    val link: String,
    val teams: List<String>,
    val imageUrl: String,
    val fullname: String
)

data class SecurityChampionMessage(
    val fallBackBlock: String,
    val messageBlock: MutableList<LayoutBlock>
)

data class SlackCommonResponse(
    val isOk: Boolean,
    val error: String? = null
)

data class SlackUserResponse(
    val isOk: Boolean,
    val users: List<User>,
    val error: String? = null
)