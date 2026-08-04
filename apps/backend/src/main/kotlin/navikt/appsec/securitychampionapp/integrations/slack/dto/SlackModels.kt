package navikt.appsec.securitychampionapp.integrations.slack.dto

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

data class SlackResponse(
    val isOk: Boolean,
    val error: String? = null
)