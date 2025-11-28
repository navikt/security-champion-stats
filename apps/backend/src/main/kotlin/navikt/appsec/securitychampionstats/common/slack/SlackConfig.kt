package navikt.appsec.securitychampionstats.common.slack

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.slack.api.Slack
import com.slack.api.methods.MethodsClient

@Configuration
class SlackConfig(
    @Value($$"${slack.token}") private val token: String
) {
    @Bean
    fun initSlackClient(): MethodsClient {
        val slackClient = Slack.getInstance()
        val methodsClient = slackClient.methods(token)
        return methodsClient
    }
}