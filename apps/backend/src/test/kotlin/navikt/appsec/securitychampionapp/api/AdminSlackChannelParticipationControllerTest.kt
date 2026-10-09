package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminSlackChannelParticipationController
import navikt.appsec.securitychampionapp.app.jobs.SlackChannelParticipationJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.membership.ChannelAttentionCategory
import navikt.appsec.securitychampionapp.app.membership.ChannelNoticeStatus
import navikt.appsec.securitychampionapp.app.membership.ChannelParticipantAttention
import navikt.appsec.securitychampionapp.app.membership.SlackChannelParticipationOverview
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

@WebMvcTest(AdminSlackChannelParticipationController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminSlackChannelParticipationControllerTest {
    @Autowired
    lateinit var mvc: MockMvc
    @MockitoBean
    lateinit var job: SlackChannelParticipationJob
    @MockitoBean
    lateinit var filter: AppAuthenticationFilter

    @Test
    fun `admin can read channel participation`() {
        authenticated(ADMIN_ROLE)
        whenever(job.overview()).thenReturn(
            SlackChannelParticipationOverview(
                true, true, Instant.EPOCH, Instant.EPOCH, "SUCCEEDED", null,
                listOf(
                    ChannelParticipantAttention(
                        UUID.randomUUID(), "Leaver", "leaver@nav.no",
                        ChannelAttentionCategory.DEACTIVATED_AFTER_LEAVING, Instant.EPOCH, ChannelNoticeStatus.SENT,
                    ),
                ),
            ),
        )

        mvc.perform(get("/api/admin/slack/channel-participation"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.participants[0].category").value("DEACTIVATED_AFTER_LEAVING"))
            .andExpect(jsonPath("$.participants[0].notificationStatus").value("SENT"))
    }

    @Test
    fun `admin can queue a check`() {
        authenticated(ADMIN_ROLE)
        whenever(job.triggerManualCheck("admin@nav.no")).thenReturn(SyncTriggerResult.STARTED)

        mvc.perform(post("/api/admin/slack/channel-participation/check")).andExpect(status().isAccepted)
    }

    @Test
    fun `disabled or running checks return problem details`() {
        authenticated(ADMIN_ROLE)
        whenever(job.triggerManualCheck(any())).thenReturn(SyncTriggerResult.DISABLED, SyncTriggerResult.ALREADY_RUNNING)

        repeat(2) {
            mvc.perform(post("/api/admin/slack/channel-participation/check"))
                .andExpect(status().isConflict)
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `non-admins cannot read or trigger channel checks`() {
        authenticated(USER_ROLE)

        mvc.perform(get("/api/admin/slack/channel-participation")).andExpect(status().isForbidden)
        mvc.perform(post("/api/admin/slack/channel-participation/check")).andExpect(status().isForbidden)
        verifyNoInteractions(job)
    }

    private fun authenticated(role: String) {
        Mockito.doAnswer { invocation ->
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("admin@nav.no", "A12345"), null, listOf(SimpleGrantedAuthority("ROLE_$role")),
            )
            try {
                invocation.getArgument<FilterChain>(2).doFilter(
                    invocation.getArgument<ServletRequest>(0), invocation.getArgument<ServletResponse>(1),
                )
            } finally {
                SecurityContextHolder.clearContext()
            }
            null
        }.`when`(filter).doFilter(any(), any(), any())
    }
}
