package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminScoringController
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustment
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(AdminScoringController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminScoringControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var scoringService: ScoringService

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should reject scoring operations for non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/scoring/participants/00000000-0000-0000-0000-000000000001/adjustments")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"pointsDelta":1,"reason":"Correction"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `should create a reasoned signed point adjustment as an admin`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val participantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        whenever(
            scoringService.addAdjustment(
                eq(participantId),
                eq(-2),
                eq("Correct a duplicate"),
                eq("admin@nav.no"),
                anyOrNull(),
            )
        ).thenReturn(
            PointAdjustment(
                id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                participantId = participantId,
                seasonId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                pointsDelta = -2,
                scoreBefore = 4,
                scoreAfter = 2,
            )
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/scoring/participants/$participantId/adjustments")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "pointsDelta" to -2,
                            "reason" to "Correct a duplicate",
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.pointsDelta").value(-2))
            .andExpect(jsonPath("$.scoreBefore").value(4))
            .andExpect(jsonPath("$.scoreAfter").value(2))

        verify(
            scoringService
        ).addAdjustment(participantId, -2, "Correct a duplicate", "admin@nav.no", null)
    }

    @Test
    fun `should reject malformed season reset dates`() {
        mockAuthenticatedUser(ADMIN_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/admin/scoring/season/reset-date")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"nextResetDate":"not-a-date"}""")
        ).andExpect(status().isBadRequest)

        verify(scoringService, org.mockito.kotlin.never()).updateNextResetDate(any(), any())
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
