package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminSlackMembershipController
import navikt.appsec.securitychampionapp.app.jobs.SlackMembershipSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SlackMembershipConfiguration
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.membership.MembershipSyncBusyException
import navikt.appsec.securitychampionapp.app.membership.SlackMembershipPreview
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import java.util.UUID

@WebMvcTest(AdminSlackMembershipController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminSlackMembershipControllerTest {
    @Autowired
    lateinit var mvc: MockMvc
    @MockitoBean
    lateinit var job: SlackMembershipSyncJob
    @MockitoBean
    lateinit var filter: AppAuthenticationFilter

    @Test
    fun `admin can read sync mode without calling Slack`() {
        authenticated(ADMIN_ROLE)
        whenever(job.configuration()).thenReturn(SlackMembershipConfiguration(false, true))

        mvc.perform(get("/api/admin/slack/membership"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.dryRun").value(true))
        verify(job).configuration()
        verifyNoMoreInteractions(job)
    }

    @Test
    fun `non-admin cannot inspect sync configuration`() {
        authenticated(USER_ROLE)

        mvc.perform(get("/api/admin/slack/membership")).andExpect(status().isForbidden)
        verifyNoInteractions(job)
    }

    @Test
    fun `admin can inspect a read-only membership preview`() {
        authenticated(ADMIN_ROLE)
        whenever(job.preview()).thenReturn(SlackMembershipPreview(setOf("U_NEW"), setOf("U_OLD"), emptySet(), 128))

        mvc.perform(get("/api/admin/slack/membership/preview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeParticipants").value(128))
            .andExpect(jsonPath("$.addedUserIds[0]").value("U_NEW"))
            .andExpect(jsonPath("$.removedUserIds[0]").value("U_OLD"))
    }

    @Test
    fun `admin can queue membership sync`() {
        authenticated(ADMIN_ROLE)
        whenever(job.triggerManualSync("admin@nav.no")).thenReturn(SyncTriggerResult.STARTED)

        mvc.perform(post("/api/admin/slack/membership/sync")).andExpect(status().isAccepted)
        verify(job).triggerManualSync("admin@nav.no")
    }

    @Test
    fun `disabled sync returns problem details`() {
        authenticated(ADMIN_ROLE)
        whenever(job.triggerManualSync(any())).thenReturn(SyncTriggerResult.DISABLED)

        mvc.perform(post("/api/admin/slack/membership/sync"))
            .andExpect(status().isConflict)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `admin explicitly resolves uncertain delivery`() {
        authenticated(ADMIN_ROLE)
        val id = UUID.randomUUID()
        whenever(job.resolveUncertain(id, true, "admin@nav.no")).thenReturn(true)

        mvc.perform(post("/api/admin/slack/membership/announcements/$id/resolve")
            .contentType(MediaType.APPLICATION_JSON).content("""{"retry":true}"""))
            .andExpect(status().isNoContent)

        verify(job).resolveUncertain(id, true, "admin@nav.no")
    }

    @Test
    fun `resolution conflicts with an active sync`() {
        authenticated(ADMIN_ROLE)
        whenever(job.resolveUncertain(any(), any(), any())).thenThrow(MembershipSyncBusyException())

        mvc.perform(post("/api/admin/slack/membership/announcements/${UUID.randomUUID()}/resolve")
            .contentType(MediaType.APPLICATION_JSON).content("""{"retry":false}"""))
            .andExpect(status().isConflict)
    }

    @ParameterizedTest
    @ValueSource(strings = ["preview", "announcements"])
    fun `non-admins cannot inspect membership operations`(path: String) {
        authenticated(USER_ROLE)

        mvc.perform(get("/api/admin/slack/membership/$path")).andExpect(status().isForbidden)
        verifyNoInteractions(job)
    }

    @Test
    fun `non-admins cannot trigger sync or retry messages`() {
        authenticated(USER_ROLE)

        mvc.perform(post("/api/admin/slack/membership/sync")).andExpect(status().isForbidden)
        mvc.perform(post("/api/admin/slack/membership/announcements/${UUID.randomUUID()}/resolve")
            .contentType(MediaType.APPLICATION_JSON).content("""{"retry":true}"""))
            .andExpect(status().isForbidden)
        verifyNoInteractions(job)
    }

    @Test
    fun `preview failures are sanitized rather than exposing integration details`() {
        authenticated(ADMIN_ROLE)
        whenever(job.preview()).thenThrow(IllegalStateException("sensitive internal details"))

        mvc.perform(get("/api/admin/slack/membership/preview"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("The request could not be completed"))
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
