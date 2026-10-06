package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminDeltaScoringController
import navikt.appsec.securitychampionapp.app.jobs.DeltaScoringSyncJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncStatusView
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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(AdminDeltaScoringController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminDeltaScoringControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: DeltaScoringStatusService

    @MockitoBean
    lateinit var syncJob: DeltaScoringSyncJob

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should return disabled sanitized Delta status to admins`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(service.status()).thenReturn(
            DeltaSyncStatusView(
                enabled = false,
                lastAttemptAt = Instant.parse("2026-10-05T12:00:00Z"),
                lastSuccessAt = null,
                outcome = "FAILED",
                eventsScanned = 0,
                creditsAwarded = 0,
                duplicateCredits = 0,
                unmatchedRegistrations = 0,
                failedEvents = 0,
                failureSummary = "Delta service authentication failed",
            )
        )

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/delta/sync-status"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.outcome").value("FAILED"))
            .andExpect(jsonPath("$.failureSummary").value("Delta service authentication failed"))
    }

    @Test
    fun `should reject Delta status requests from non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/delta/sync-status"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should queue Delta sync as an admin`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(syncJob.triggerManualSync("admin@nav.no")).thenReturn(SyncTriggerResult.STARTED)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/delta/sync"))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `should reject manual Delta sync triggers when disabled`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(syncJob.triggerManualSync("admin@nav.no")).thenReturn(SyncTriggerResult.DISABLED)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/delta/sync"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `should reject manual Delta sync triggers from non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/delta/sync"))
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
