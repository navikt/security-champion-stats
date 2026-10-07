package navikt.appsec.securitychampionapp.config

import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("slack.membership")
data class SlackMembershipProperties(
    val enabled: Boolean = false,
    val dryRun: Boolean = true,
    val usergroupId: String = "",
    val welcomeChannelId: String = "",
    val adminChannelId: String = "",
) {
    fun settings() = SlackMembershipSettings(usergroupId, welcomeChannelId, adminChannelId)
}

@Configuration
@EnableConfigurationProperties(SlackMembershipProperties::class)
class SlackMembershipConfig {
    @Bean
    fun slackMembershipService(
        participants: ParticipantStore,
        directory: SlackParticipantDirectory,
        slack: SlackMembershipGateway,
        roles: ChampionRoleSource,
        state: SlackMembershipStore,
        properties: SlackMembershipProperties,
    ): SlackMembershipService {
        val settings = properties.settings()
        if (properties.enabled) settings.validate()
        return SlackMembershipService(participants, directory, slack, roles, state, settings)
    }
}
