package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminScoringController
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustment
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@WebMvcTest(AdminScoringController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class, ScoringService::class)
class AdminScoringControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var scoringRepository: ScoringLedger

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
            scoringRepository.addAdjustment(
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
            scoringRepository
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

        verify(scoringRepository, org.mockito.kotlin.never()).updateNextResetDate(any(), any())
    }

    @Test
    fun `should reject a season reset without confirmation before changing stored state`() {
        mockAuthenticatedUser(ADMIN_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/scoring/season/reset")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"reason":"Program reset"}""")
        ).andExpect(status().isBadRequest)

        verifyNoInteractions(scoringRepository)
    }

    @Test
    fun `should reject an explicitly unconfirmed season reset before changing stored state`() {
        mockAuthenticatedUser(ADMIN_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/scoring/season/reset")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"confirmed":false,"reason":"Program reset"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Confirmation is required"))

        verifyNoInteractions(scoringRepository)
    }

    @Test
    fun `should return a problem detail for an invalid adjustment`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val participantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/scoring/participants/$participantId/adjustments")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"pointsDelta":0,"reason":"Correction"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.detail").value("The adjustment must not be zero"))

        verifyNoInteractions(scoringRepository)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        """{"confirmed":true}""",
        """{"confirmed":true,"reason":null}""",
        """{"confirmed":true,"reason":""}""",
        """{"confirmed":true,"reason":"   "}""",
    ])
    fun `should reject a season reset without a nonblank reason before changing stored state`(body: String) {
        mockAuthenticatedUser(ADMIN_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/scoring/season/reset")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(body)
        ).andExpect(status().isBadRequest)

        verifyNoInteractions(scoringRepository)
    }

    @Test
    fun `should reset a season when an admin confirms with a reason`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val today = LocalDate.now(ZoneId.of("Europe/Oslo"))
        val nextResetDate = today.plusMonths(3)
        val previousSeason = SeasonSummary(UUID.randomUUID(), today.minusDays(1), null, nextResetDate)
        val newSeason = SeasonSummary(UUID.randomUUID(), today, null, nextResetDate)
        whenever(scoringRepository.currentSeason()).thenReturn(previousSeason)
        whenever(scoringRepository.resetManually(today, "Program reset", "admin@nav.no")).thenReturn(newSeason)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/scoring/season/reset")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"confirmed":true,"reason":"  Program reset  "}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(newSeason.id.toString()))
            .andExpect(jsonPath("$.startsOn").value(today.toString()))
            .andExpect(jsonPath("$.nextResetDate").value(nextResetDate.toString()))

        verify(scoringRepository).resetManually(today, "Program reset", "admin@nav.no")
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
