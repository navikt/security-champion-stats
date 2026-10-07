package navikt.appsec.securitychampionapp.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import tools.jackson.databind.ObjectMapper

class TokenIntrospectionTest {
    private val tokenClient = mock<TokenValidationClient>()

    @Test
    fun `should prevent startup when the admin group setting is missing`() {
        ApplicationContextRunner()
            .withBean(PropertySourcesPlaceholderConfigurer::class.java, { PropertySourcesPlaceholderConfigurer() })
            .withBean(TokenValidationClient::class.java, { tokenClient })
            .withUserConfiguration(TokenIntrospection::class.java)
            .withPropertyValues(
                "spring.security.token-validation.identity-provider=entra_id",
                "spring.security.token-validation.url=http://localhost/introspect",
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasStackTraceContaining("spring.security.token-validation.groups")
            }
    }

    @Test
    fun `should prevent startup when the admin group is blank`() {
        for (group in listOf("", "   ")) {
            ApplicationContextRunner()
                .withBean(TokenIntrospection::class.java, {
                    TokenIntrospection(
                        tokenClient,
                        "entra_id",
                        "http://localhost/introspect",
                        group,
                        mock<ObjectMapper>(),
                    )
                })
                .run { context ->
                    assertThat(context).hasFailed()
                    assertThat(context.startupFailure)
                        .hasRootCauseMessage("Admin group configuration must not be blank")
                }
        }
    }
}
