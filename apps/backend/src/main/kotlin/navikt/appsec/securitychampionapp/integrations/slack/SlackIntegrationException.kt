package navikt.appsec.securitychampionapp.integrations.slack

class SlackIntegrationException(operation: Operation, errorCode: String?) :
    RuntimeException(summary(operation, errorCode)) {
    enum class Operation(val apiMethod: String) {
        HISTORY("conversations.history"),
        REPLIES("conversations.replies"),
        USERS_INFO("users.info"),
        USERS_LIST("users.list"),
        LOOKUP_BY_EMAIL("users.lookupByEmail"),
        GROUP_MEMBERS("usergroups.users.list"),
        GROUP_UPDATE("usergroups.users.update"),
        POST_MESSAGE("chat.postMessage"),
    }

    companion object {
        private val guidance = mapOf(
            "not_in_channel" to "invite the app to the configured channel",
            "channel_not_found" to "verify the channel ID and token access",
            "missing_scope" to "check token scopes for this method and channel type",
            "not_allowed_token_type" to "use a token type supported by this method",
            "invalid_auth" to "check the configured Slack token",
            "not_authed" to "configure a Slack token",
            "token_revoked" to "replace the revoked Slack token",
            "token_expired" to "refresh the Slack token",
            "account_inactive" to "check the Slack account or app installation",
            "ratelimited" to "retry after the Slack rate limit clears",
            "thread_not_found" to "check whether the thread still exists",
            "internal_error" to "retry after the Slack service recovers",
            "fatal_error" to "retry after the Slack service recovers",
        )

        private fun summary(operation: Operation, errorCode: String?): String {
            val code = errorCode ?: "no response"
            val action = guidance[code] ?: "check Slack availability and API access"
            return "Slack ${operation.apiMethod} failed: $code; $action"
        }
    }
}
