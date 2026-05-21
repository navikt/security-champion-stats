package navikt.appsec.securitychampionapp

import navikt.appsec.securitychampionapp.app.api.Controller
import navikt.appsec.securitychampionapp.app.api.dto.Member
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackService
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.security.TokenValidationClient
import navikt.appsec.securitychampionapp.security.dto.TokenResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content


@WebMvcTest(
    Controller::class,
    properties = [
        "spring.security.token-validation.groups=test123",
        "spring.security.token-validation.identity-provider=test-identity-provider",
        "spring.security.token-validation.url=http://test-url.com"
    ]
)
@Import(SecurityConfig::class)
class ControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var repo: PostgresRepository
    @MockitoBean lateinit var tokenValidationClient: TokenValidationClient
    @MockitoBean lateinit var catalog: TeamCatalog
    @MockitoBean lateinit var slackService: SlackService

    val tokenResponse = TokenResponse(
        active = true,
        preferredUsername = "admin@nav.no",
        ident = "test123",
        groups = listOf("test-group", "test-group-2", "test-group-3"),
        error = null
    )
    private val memberList = listOf(
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


    @Test
    fun `Missing Authorization header when calling endpoint should fail`() {
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
        )).thenReturn(TokenResponse(active = false, error = "Invalid token", ident = null, preferredUsername = null))

        mockMvc.get("/api/members") { header("Authorization", "Bearer badtoken")}
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `Test get all members successfully from database`() {
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(tokenResponse)

        `when`(repo.getAllMembersInProgram()).thenReturn(
            memberList
        )
        mockMvc.get("/api/members") { header("Authorization", "Bearer usertoken")}
            .andExpect{status { isOk() } }
    }

    @Test
    fun `Test get all members successfully from database, with empty database`() {
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(tokenResponse)

        `when`(repo.getAllMembers()).thenReturn(
            emptyList()
        )
        `when`(catalog.fetchMembersWithRole()).thenReturn(
            emptyList()
        )
        mockMvc.
            get("/api/members") { header("Authorization", "Bearer usertoken")}
            .andExpect { status { isOk() } }
            .andExpect { content().json("{}") }
    }

    @Test
    fun `Test a successful join program and then leaving program`() {
        `when`(tokenValidationClient.validate(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )).thenReturn(tokenResponse)

        `when`(repo.getMemberByEmail(Mockito.anyString())).thenReturn(
            Member(
                "test-id-1",
                "test name",
                0,
                null,
                "test@nav.no",
                inProgram = true
            )
        )
        doNothing().`when`(repo).updateInProgram("test@nav.no", true)
        doNothing().`when`(repo).updateInProgram("test@nav.no", false)

        val joinResult = mockMvc.perform(
            post("/api/join")
                .header("Authorization", "Bearer usertoken")
                .contentType("Application/json")
                .content("test@nav.no")
        ).andReturn()

        val leaveResult = mockMvc.perform(
            post("/api/leave")
                .header("Authorization", "Bearer usertoken")
                .contentType("Application/json")
                .content("test@nav.no")
        ).andReturn()

        assertThat(joinResult.response.status == 200)
        assertThat { leaveResult.response.status == 200 }
    }
}