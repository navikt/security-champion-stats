package navikt.appsec.securitychampionapp.security

import navikt.appsec.securitychampionapp.security.dto.TokenResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
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
    fun `should expose the validated bearer token as protected credentials`() {
        whenever(tokenClient.validate(any(), any(), any())).thenReturn(
            TokenResponse(
                active = true,
                preferredUsername = "user@example.test",
                ident = "TEST123",
                groups = emptyList(),
                error = null,
            ),
        )
        val filter = TokenIntrospection(
            tokenClient,
            "entra_id",
            "http://localhost/introspect",
            "test-admin-group",
            mock<ObjectMapper>(),
        )
        val request = MockHttpServletRequest("GET", "/api/member").apply {
            servletPath = "/api/member"
            addHeader("Authorization", "Bearer user-token")
        }
        var authentication: Authentication? = null

        try {
            filter.doFilter(request, MockHttpServletResponse()) { _, _ ->
                authentication = SecurityContextHolder.getContext().authentication
            }
        } finally {
            SecurityContextHolder.clearContext()
        }

        assertThat(authentication?.credentials).isEqualTo("user-token")
        assertThat(authentication.toString()).doesNotContain("user-token")
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
