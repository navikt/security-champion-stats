package navikt.appsec.securitychampionstats.stats

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.security.azure.SecurityConfig
import navikt.appsec.securitychampionstats.common.security.client.TokenValidationClient
import navikt.appsec.securitychampionstats.common.security.dto.TokenResponse
import navikt.appsec.securitychampionstats.common.slack.SlackService
import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.common.teams.GraphClient
import navikt.appsec.securitychampionstats.stats.dto.Member
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(
    controllers = [
        Controller::class,
        AdminController::class
    ]
)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class ControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var repo: PostgresRepository
    @MockitoBean lateinit var tokenValidationClient: TokenValidationClient
    @MockitoBean lateinit var catalog: TeamCatalog
    @MockitoBean lateinit var slackService: SlackService
    @MockitoBean lateinit var graphClient: GraphClient

    private val memberList = listOf<Member>(
        Member(
            "test-id-1",
            "test-name-1",
            0,
            null,
            "test-email-1"
        ),
        Member(
            "test-id-2",
            "test-name-2",
            10,
            null,
            "test-email-2"
        )
    )

    private val claim = mapOf(
        "preferred_username" to JsonPrimitive("test@test.com"),
        "NAVident" to JsonPrimitive("123456"),
        "groups" to JsonArray(listOf(
            JsonPrimitive("1222"),
            JsonPrimitive("1234"),
            JsonPrimitive("test-id-4b9d-984e-85499f126e18")
        )),
        "others" to JsonPrimitive("others")
    )

    @Test
    fun `Missing Authorization header when calling endpoint`() {
        mockMvc.get("/api/members")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `Calling upon endpoint with inactive token`() {
        `when`(tokenValidationClient.validate(
            "test-validation-url",
            "badtoken",
            "entra"
        )).thenReturn(TokenResponse(active = false, claims = emptyMap()))

        mockMvc.get("/api/members") { header("Authorization", "Bearer badtoken")}
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `Calling upon admin endpoint, with normal user`() {
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(TokenResponse(active = true, claims = claim))

        mockMvc.perform(
            delete("/api/admin/member")
                .header("Authorization", "Bearer usertoken")
                .contentType("application/json")
                .content("{\"id\":\"test-id-1\"}")
        )
            .andExpect { status().isForbidden }
    }

    @Test
    fun `Calling upon admin endpoint, with admin user`() {
        claim.map { (key, _) ->
            if (key == "groups") {
                JsonPrimitive(" [ \"1222\", \"1234\", \"test-id-4b9d-984e-85499f126e18\"]")
            }

        }
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(TokenResponse(true, claim))
        doNothing().`when`(repo).deleteMember("test-id-1")

        mockMvc.perform(
            delete("/api/admin/member")
                .header("Authorization", "Bearer usertoken")
                .contentType("application/json")
                .content("{\"id\":\"test-id-1\"}")
        )
            .andExpect {
                status().isOk
            }
    }

    @Test
    fun `Test get all members successfully from database`() {
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(TokenResponse(active = true, claims = claim))

        `when`(repo.getAllMembers()).thenReturn(
            memberList
        )
        mockMvc.perform(
            get("/api/members")
                .header("Authorization", "Bearer usertoken")
        )
            .andExpect(status().isOk())
            .andExpect(content().json(Json.encodeToString(memberList)))
    }
}