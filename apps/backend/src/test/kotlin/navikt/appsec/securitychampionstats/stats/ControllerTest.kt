package navikt.appsec.securitychampionstats.stats

import kotlinx.serialization.json.Json
import navikt.appsec.securitychampionstats.integration.postgres.PostgresRepository
import navikt.appsec.securitychampionstats.integration.slack.SlackService
import navikt.appsec.securitychampionstats.integration.slack.dto.SlackActivitySummary
import navikt.appsec.securitychampionstats.integration.slack.dto.UserInfo
import navikt.appsec.securitychampionstats.integration.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.integration.teams.GraphClient
import navikt.appsec.securitychampionstats.stats.dto.Member
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(Controller::class)
@AutoConfigureMockMvc
class ControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var repo: PostgresRepository
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

    @Test
    fun `Test get all members successfully from database`() {
        `when`(repo.getAllMembers()).thenReturn(
            memberList
        )
        mockMvc.perform(get("/api/members"))
            .andExpect(status().isOk())
            .andExpect(content().json(Json.encodeToString(memberList)))
    }

    @Test
    fun `Test get all members with empty list from database`() {
        `when`(repo.getAllMembers()).thenReturn(
            emptyList()
        )

        mockMvc.perform(get("/api/members"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"))
    }

    @Test
    fun `Test delete member endpoint`() {

        doNothing().`when`(repo).deleteMember("test-id-1")
        mockMvc.perform(
            delete("/api/member")
                .contentType("application/json")
                .content("{\"id\":\"test-id-1\"}")
        )
            .andExpect(status().isAccepted())
    }

    @Test
    fun `Test add member endpoint`() {
        doNothing().`when`(repo).addMember("test-name-1", "test-id-1", "test-email-1")
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/member")
                .contentType("application/json")
                .content("{\"fullname\":\"test-name-1\",\"id\":\"test-id-1\",\"email\":\"test-email-1\"}")
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `Test add points endpoint`() {
        `when`(repo.addPoints("test-id-1", 10)).thenReturn(1)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/points")
                .contentType("application/json")
                .content("{\"id\":\"test-id-1\",\"points\":10}")
        )
            .andExpect(status().isAccepted)
    }

    @Test
    fun `Test slack endpoint`() {
        `when`(repo.getMember("testId")).thenReturn(
            Member(
                id = "testId",
                fullname = "Tester",
                points = 0,
                lastUpdated = null,
                email = "test-email")
        )
        `when`(slackService.summarizeActivity("test-email")).thenReturn(
            SlackActivitySummary(
                UserInfo(
                    userId = "testId",
                    fullname = "Tester"
                ),
                setOf("test"),
                emptyMap(),
                5
            )
        )
        `when`(repo.addPoints("test-email", 5 * 10)).thenReturn(1)
        mockMvc.perform(
            get("/api/slack")
        )
            .andExpect(status().isOk)
    }


}