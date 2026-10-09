package navikt.appsec.securitychampionapp.config

import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@ConfigurationProperties("slack.channel-participation")
data class SlackChannelParticipationProperties(
    val enabled: Boolean = false,
    val maxDeparturesPerCheck: Int = 5,
)

@Configuration
@EnableConfigurationProperties(SlackChannelParticipationProperties::class)
class SlackChannelParticipationConfig {
    @Bean
    fun slackChannelParticipationSettings(
        @Value($$"${slack.sc-channel-id}") channelId: String,
        properties: SlackChannelParticipationProperties,
    ): SlackChannelParticipationSettings =
        SlackChannelParticipationSettings(channelId, properties.maxDeparturesPerCheck).also {
            if (properties.enabled) it.validate()
        }

    @Bean
    fun slackChannelParticipationService(
        participants: ParticipantStore,
        directory: SlackParticipantDirectory,
        slack: SlackChannelGateway,
        store: SlackChannelParticipationStore,
        settings: SlackChannelParticipationSettings,
        clock: Clock,
    ): SlackChannelParticipationService =
        SlackChannelParticipationService(participants, directory, slack, store, settings, clock)
}
