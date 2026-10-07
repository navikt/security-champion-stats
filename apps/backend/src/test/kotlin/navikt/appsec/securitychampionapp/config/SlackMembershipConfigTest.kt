package navikt.appsec.securitychampionapp.config

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock

class SlackMembershipConfigTest {
    @ParameterizedTest
    @ValueSource(strings = ["group", "welcome", "admin"])
    fun `enabled membership sync requires every destination ID`(missing: String) {
        val configured = SlackMembershipProperties(
            enabled = true, usergroupId = "S_GROUP", welcomeChannelId = "C_WELCOME", adminChannelId = "C_ADMIN",
        )
        val properties = when (missing) {
            "group" -> configured.copy(usergroupId = "")
            "welcome" -> configured.copy(welcomeChannelId = "")
            else -> configured.copy(adminChannelId = "")
        }

        assertThatThrownBy {
            SlackMembershipConfig().slackMembershipService(mock(), mock(), mock(), mock(), mock(), properties)
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("must be configured")
    }
}
