package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminPlaybookEventImportController
import navikt.appsec.securitychampionapp.app.jobs.PlaybookEventImportJob
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

@WebMvcTest(AdminPlaybookEventImportController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminPlaybookEventImportControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var importJob: PlaybookEventImportJob

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should accept a manual import from an administrator`() {
        authenticate(ADMIN_ROLE)
        whenever(importJob.triggerManualImport()).thenReturn(SyncTriggerResult.STARTED)
        mockMvc.perform(post("/api/admin/playbook/events/sync")).andExpect(status().isAccepted)
    }

    @Test
    fun `should report disabled busy and unavailable manual imports`() {
        authenticate(ADMIN_ROLE)
        whenever(importJob.triggerManualImport()).thenReturn(
            SyncTriggerResult.DISABLED, SyncTriggerResult.ALREADY_RUNNING, SyncTriggerResult.UNAVAILABLE,
        )
        mockMvc.perform(post("/api/admin/playbook/events/sync")).andExpect(status().isConflict)
        mockMvc.perform(post("/api/admin/playbook/events/sync")).andExpect(status().isConflict)
        mockMvc.perform(post("/api/admin/playbook/events/sync")).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `should reject non administrator imports`() {
        authenticate(USER_ROLE)
        mockMvc.perform(post("/api/admin/playbook/events/sync")).andExpect(status().isForbidden)
        verifyNoInteractions(importJob)
    }

    private fun authenticate(role: String) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val chain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("admin@nav.no", "A12345"), null, listOf(SimpleGrantedAuthority("ROLE_$role")),
            )
            try {
                chain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }
            null
        }.`when`(introspectionFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any())
    }
}
