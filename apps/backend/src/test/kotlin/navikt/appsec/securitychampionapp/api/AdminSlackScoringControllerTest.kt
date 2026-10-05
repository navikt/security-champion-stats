package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminSlackScoringController
import navikt.appsec.securitychampionapp.app.scoring.SlackScoringService
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(AdminSlackScoringController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminSlackScoringControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var slackScoringService: SlackScoringService

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should create an explicitly approved Slack mapping as an admin`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val participantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        whenever(slackScoringService.addMapping("U_SLACK", participantId, "admin@nav.no")).thenReturn(true)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/slack/mappings")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"slackUserId":"U_SLACK","participantId":"$participantId"}""")
        ).andExpect(status().isCreated)

        verify(slackScoringService).addMapping("U_SLACK", participantId, "admin@nav.no")
    }

    @Test
    fun `should reject Slack mapping changes for non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/slack/mappings")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"slackUserId":"U_SLACK","participantId":"00000000-0000-0000-0000-000000000001"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `should reject conflicting Slack mappings`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val participantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        whenever(slackScoringService.addMapping(any(), eq(participantId), eq("admin@nav.no")))
            .thenThrow(DuplicateKeyException("mapping conflict"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/slack/mappings")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"slackUserId":"U_SLACK","participantId":"$participantId"}""")
        ).andExpect(status().isConflict)
    }

    private fun mockAuthenticatedUser(role: String) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val filterChain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("admin@nav.no", "A12345", "admin@nav.no"),
                null,
                listOf(SimpleGrantedAuthority("ROLE_$role")),
            )
            try {
                filterChain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }
            null
        }.`when`(introspectionFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any())
    }
}
