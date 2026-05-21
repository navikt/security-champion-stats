package navikt.appsec.securitychampionapp

import navikt.appsec.securitychampionapp.app.api.AdminController
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.security.TokenValidationClient
import navikt.appsec.securitychampionapp.security.dto.TokenResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doNothing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    AdminController::class,
    properties = [
        "spring.security.token-validation.groups=test123",
        "spring.security.token-validation.identity-provider=test-identity-provider",
        "spring.security.token-validation.url=http://test-url.com"
    ]
)
@Import(SecurityConfig::class)
class AdminControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var repo: PostgresRepository
    @MockitoBean lateinit var tokenValidationClient: TokenValidationClient

    val tokenResponse = TokenResponse(
        active = true,
        preferredUsername = "admin@nav.no",
        ident = "test123",
        groups = listOf("test123", "test-group-2", "test-group-3"),
        error = null
    )

    @Test
    fun `Trying access admin endpoint without admin role should return forbidden`() {
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(TokenResponse(
            true,
            preferredUsername = "user@nav.no",
            ident = "test1234",
            groups = emptyList(),
            error = null
        ))

        doNothing().`when`(repo).addMember(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )

        val request = """
            {
              "fullname": "Test User",
              "email": "test@nav.no"
            }
        """.trimIndent()

        val result = mockMvc.perform (
            post("/api/admin/member")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(request)
        ).andReturn()

        assertThat(result.response.status).isEqualTo(403)
    }

    @Test
    fun `Trying access admin endpoint with admin role should return ok`() {
        `when`(
            tokenValidationClient.validate(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(tokenResponse)

        doNothing().`when`(repo).addMember(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )
        val request = """
            {
              "fullname": "Test User",
              "email": "test@nav.no"
            }
        """.trimIndent()
        val result = mockMvc.perform(
            post("/api/admin/member")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(request)
        ).andExpect { status().isCreated() }
            .andReturn()
        val error = result.resolvedException
        assert(error == null)
        assert(result.response.status == 201)
        assert(result.response.contentAsString.contains("User was created"))
    }

    @Test
    fun `Trying to delete member with admin role should return accepted`() {
        `when`(
            tokenValidationClient.validate(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(tokenResponse)
        doNothing().`when`(repo).deleteMember("test@nav.no")

        val result = mockMvc.perform (
            delete("/api/admin/member/test@nav.no")
                .header("Authorization", "Bearer test-token")
        ).andReturn()

        val error = result.resolvedException
        assert(error == null)
        assert(result.response.status == 202)
    }
}
