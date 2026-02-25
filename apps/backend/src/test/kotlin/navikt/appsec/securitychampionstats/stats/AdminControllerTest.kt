package navikt.appsec.securitychampionstats.stats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.security.azure.SecurityConfig
import navikt.appsec.securitychampionstats.common.security.client.TokenValidationClient
import navikt.appsec.securitychampionstats.common.security.dto.TokenResponse
import navikt.appsec.securitychampionstats.stats.dto.MemberInfo
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doNothing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(
    controllers = [
        AdminController::class
    ]
)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class AdminControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var repo: PostgresRepository
    @MockitoBean lateinit var tokenValidationClient: TokenValidationClient

    private val claim = mapOf(
        "preferred_username" to JsonPrimitive("test@test.com"),
        "NAVident" to JsonPrimitive("test-id"),
        "groups" to JsonArray(listOf(
            JsonPrimitive("test-group"),
            JsonPrimitive("test-group-2"),
            JsonPrimitive("test-group-3")
        )),
        "others" to JsonPrimitive("other-claim")
    )

    @Test
    fun `Trying access admin endpoint without admin role should return forbidden`() {
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(TokenResponse(active = false, claims = claim))


        mockMvc.get("/api/admin/member") {
            header("Authorization", "Bearer test-token")
        }
            .andExpect { status { isUnauthorized() }}
    }

    @Test
    fun `Trying access admin endpoint with admin role should return ok`() {
        `when`(
            tokenValidationClient.validate(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(TokenResponse(active = true, claims = claim))

        doNothing().`when`(repo).addMember(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )

        mockMvc.perform(
            post("/api/admin/member")
                .header("Authorization", "Bearer test-token")
                .contentType("Application/json")
                .content(
                    """{ "email": "test@email.com", "fullname": "Test User" }"""
                )
        ).andExpect(status().isCreated)
    }

    @Test
    fun `Trying to delete member with admin role should return accepted`() {
        `when`(
            tokenValidationClient.validate(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(TokenResponse(active = true, claims = claim))

        doNothing().`when`(repo).deleteMember("")
    }
}