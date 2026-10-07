package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import navikt.appsec.securitychampionapp.app.api.AdminGitHubScoringController
import navikt.appsec.securitychampionapp.app.jobs.GitHubScoringSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminGitHubScoringController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminGitHubScoringControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var job: GitHubScoringSyncJob
    @MockitoBean lateinit var filter: AppAuthenticationFilter

    @Test
    fun `should accept admin sync requests and expose busy disabled and unavailable results`() {
        authenticate(ADMIN_ROLE)
        mapOf(
            SyncTriggerResult.STARTED to 202,
            SyncTriggerResult.ALREADY_RUNNING to 409,
            SyncTriggerResult.DISABLED to 409,
            SyncTriggerResult.UNAVAILABLE to 503,
        ).forEach { (result, code) ->
            whenever(job.triggerManualSync("admin@nav.no")).thenReturn(result)
            mvc.perform(post("/api/admin/github/sync")).andExpect(status().`is`(code))
        }
    }

    @Test
    fun `should forbid sync requests from non administrators`() {
        authenticate(USER_ROLE)
        mvc.perform(post("/api/admin/github/sync")).andExpect(status().isForbidden)
        verifyNoInteractions(job)
    }

    private fun authenticate(role: String) {
        Mockito.doAnswer { invocation ->
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("admin@nav.no", "A12345"), null,
                listOf(SimpleGrantedAuthority("ROLE_$role")),
            )
            try {
                invocation.getArgument<FilterChain>(2).doFilter(
                    invocation.getArgument(0), invocation.getArgument(1),
                )
            } finally {
                SecurityContextHolder.clearContext()
            }
            null
        }.`when`(filter).doFilter(Mockito.any(), Mockito.any(), Mockito.any())
    }
}
