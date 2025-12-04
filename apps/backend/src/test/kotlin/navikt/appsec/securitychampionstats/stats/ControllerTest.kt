package navikt.appsec.securitychampionstats.stats

import kotlinx.serialization.json.Json
import navikt.appsec.securitychampionstats.common.hikari.DataSource
import navikt.appsec.securitychampionstats.integration.postgres.PostgresRepository
import navikt.appsec.securitychampionstats.integration.slack.SlackService
import navikt.appsec.securitychampionstats.integration.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.integration.teams.GraphClient
import navikt.appsec.securitychampionstats.integration.zoom.ZoomMeetingService
import navikt.appsec.securitychampionstats.stats.dto.Member
import org.json.JSONArray
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(Controller::class)
@AutoConfigureMockMvc
class ControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var repo: PostgresRepository
    @MockitoBean lateinit var catalog: TeamCatalog
    @MockitoBean lateinit var slackService: SlackService
    @MockitoBean lateinit var zoomService: ZoomMeetingService
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

    @Test
    fun `Test get all members successfully from database`() {
        `when`(repo.getAllMembers()).thenReturn(
            memberList
        )
        mockMvc.perform(get("/api/members"))
            .andExpect(status().isOk())
            .andExpect(content().json(Json.encodeToString(memberList)))
    }
}