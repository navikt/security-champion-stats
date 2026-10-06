package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminDeltaEventImportController
import navikt.appsec.securitychampionapp.app.jobs.DeltaEventImportJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminDeltaEventImportController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminDeltaEventImportControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var importJob: DeltaEventImportJob

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should queue Delta event import as an admin`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(importJob.triggerManualImport()).thenReturn(SyncTriggerResult.STARTED)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/delta/events/sync"))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `should reject Delta event import when an import is already running`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(importJob.triggerManualImport()).thenReturn(SyncTriggerResult.ALREADY_RUNNING)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/delta/events/sync"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `should reject Delta event import from non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/delta/events/sync"))
            .andExpect(status().isForbidden)
    }

    private fun mockAuthenticatedUser(role: String) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val filterChain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("admin@nav.no", "A12345"),
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
