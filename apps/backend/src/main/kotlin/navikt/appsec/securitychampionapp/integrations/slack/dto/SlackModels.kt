package navikt.appsec.securitychampionapp.integrations.slack.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

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

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackFetchUserResponse(
    val responses: SlackFetchUserVariants,
    val failure: SlackFetchUserFailure
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackFetchUserVariants(
    val success: SlackFetchUserSuccess
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackFetchUserSuccess(
    val ok: Boolean,
    val user: SlackMockUser
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackFetchUserFailure(
    val ok: Boolean,
    val error: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackMockUser(
    val id: String,
    val name: String,
    @JsonProperty("real_name") val realName: String?,
    val profile: SlackMockProfile?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlackMockProfile(
    val email: String?,
    @JsonProperty("image_original") val imageOriginal: String?,
    @JsonProperty("image_192") val image192: String?
)