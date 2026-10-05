package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminDashboardController
import navikt.appsec.securitychampionapp.app.dashboard.AdminDashboardOverview
import navikt.appsec.securitychampionapp.app.dashboard.AdminDashboardService
import navikt.appsec.securitychampionapp.app.dashboard.CreditTypeTotal
import navikt.appsec.securitychampionapp.app.dashboard.WeeklyCreditTotals
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncStatusView
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.SlackSyncStatusView
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
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(AdminDashboardController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminDashboardControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: AdminDashboardService

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should return aggregate metrics and sanitized integration statuses to admins`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(service.overview()).thenReturn(
            AdminDashboardOverview(
                season = SeasonSummary(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    startsOn = LocalDate.parse("2026-01-01"),
                    endsOn = null,
                    nextResetDate = LocalDate.parse("2027-01-01"),
                ),
                today = LocalDate.parse("2026-10-05"),
                activeParticipantCount = 4,
                eventRegistrationCount = 3,
                pointsByCreditType = listOf(CreditTypeTotal("DELTA_REGISTRATION", 3)),
                weeklyTotals = listOf(
                    WeeklyCreditTotals(
                        weekStarting = LocalDate.parse("2026-10-05"),
                        pointsByCreditType = mapOf("DELTA_REGISTRATION" to 1),
                    ),
                ),
                slack = SlackSyncStatusView(
                    enabled = true,
                    lastAttemptAt = Instant.parse("2026-10-05T12:00:00Z"),
                    lastSuccessAt = Instant.parse("2026-10-05T12:00:00Z"),
                    outcome = "FAILED",
                    messagesScanned = 0,
                    creditsAwarded = 0,
                    duplicateCredits = 0,
                    unmappedAuthors = 0,
                    failureSummary =
                        "Slack activity could not be synchronized; check API access and channel configuration",
                ),
                delta = DeltaSyncStatusView(
                    enabled = true,
                    lastAttemptAt = Instant.parse("2026-10-05T12:00:00Z"),
                    lastSuccessAt = Instant.parse("2026-10-05T12:00:00Z"),
                    outcome = "PARTIAL_FAILURE",
                    eventsScanned = 2,
                    creditsAwarded = 1,
                    duplicateCredits = 0,
                    unmatchedRegistrations = 0,
                    failedEvents = 1,
                    failureSummary = "Delta registration sync failed; verify service connectivity",
                ),
            ),
        )

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/dashboard/overview"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.activeParticipantCount").value(4))
            .andExpect(jsonPath("$.eventRegistrationCount").value(3))
            .andExpect(jsonPath("$.pointsByCreditType[0].creditType").value("DELTA_REGISTRATION"))
            .andExpect(jsonPath("$.weeklyTotals[0].weekStarting").value("2026-10-05"))
            .andExpect(jsonPath("$.slack.outcome").value("FAILED"))
            .andExpect(
                jsonPath("$.slack.failureSummary").value(
                    "Slack activity could not be synchronized; check API access and channel configuration",
                ),
            )
            .andExpect(jsonPath("$.delta.outcome").value("PARTIAL_FAILURE"))
            .andExpect(
                jsonPath("$.delta.failureSummary").value(
                    "Delta registration sync failed; verify service connectivity",
                ),
            )
            .andExpect(jsonPath("$.participants").doesNotExist())
            .andExpect(jsonPath("$.slack.rawPayload").doesNotExist())
    }

    @Test
    fun `should reject dashboard access for non-admin employees`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/dashboard/overview"))
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
