package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.ScoreHistoryController
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.ScoreHistoryRecord
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(ScoreHistoryController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class, ScoringService::class)
class ScoreHistoryControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var participantRepository: ParticipantStore

    @MockitoBean
    lateinit var scoringRepository: ScoringLedger

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `participant endpoint returns no operational identifiers or adjustment details`() {
        val participantId = UUID.randomUUID()
        mockAuthenticatedUser(USER_ROLE)
        whenever(participantRepository.findByNavNoEmail("person@nav.no")).thenReturn(participant(participantId))
        whenever(scoringRepository.participantExists(participantId)).thenReturn(true)
        whenever(scoringRepository.scoreHistoryPage(participantId, null, "all", null, 26)).thenReturn(
            listOf(
                ScoreHistoryRecord(
                    id = "internal-credit-id",
                    type = "CREDIT",
                    recordedAt = Instant.parse("2026-10-01T10:00:00Z"),
                    activityAt = Instant.parse("2026-10-01T09:00:00Z"),
                    seasonId = UUID.randomUUID(),
                    creditType = ActivityCreditType.GITHUB_COMMIT,
                    points = 2,
                    displayName = "Example contribution",
                    sourceReference = "private/source-reference",
                    creditId = "internal-credit-id",
                    reason = "private adjustment reason",
                    adminName = "private-admin@nav.no",
                    revokesCreditId = "internal-revoked-id",
                    membershipAction = null,
                    tieIndex = 1,
                ),
            ),
        )

        mockMvc.perform(get("/api/me/score-history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries[0].kind").value("credit"))
            .andExpect(jsonPath("$.entries[0].occurredAt").value("2026-10-01T09:00:00Z"))
            .andExpect(jsonPath("$.entries[0].points").value(2))
            .andExpect(jsonPath("$.entries[0].id").doesNotExist())
            .andExpect(jsonPath("$.entries[0].recordedAt").doesNotExist())
            .andExpect(jsonPath("$.entries[0].sourceRef").doesNotExist())
            .andExpect(jsonPath("$.entries[0].sourceReference").doesNotExist())
            .andExpect(jsonPath("$.entries[0].creditId").doesNotExist())
            .andExpect(jsonPath("$.entries[0].seasonId").doesNotExist())
            .andExpect(jsonPath("$.entries[0].reason").doesNotExist())
            .andExpect(jsonPath("$.entries[0].adminName").doesNotExist())
            .andExpect(jsonPath("$.entries[0].revokesCreditId").doesNotExist())
        verify(scoringRepository).scoreHistoryPage(participantId, null, "all", null, 26)
    }

    @Test
    fun `admin participant history is forbidden to regular users`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(get("/api/participants/${UUID.randomUUID()}/score-history"))
            .andExpect(status().isForbidden)

        verifyNoInteractions(scoringRepository)
        verify(participantRepository, never()).findByNavNoEmail(any())
    }

    private fun participant(id: UUID) = ProgramParticipant(
        id = id,
        navNoEmail = "person@nav.no",
        navIdent = "A12345",
        email = "person@nav.no",
        fullname = "Person",
        teams = emptyList(),
        status = ParticipationStatus.ACTIVE,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun mockAuthenticatedUser(role: String) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val filterChain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("person@nav.no", "A12345"),
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
