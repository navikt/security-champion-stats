package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.ScoringController
import navikt.appsec.securitychampionapp.app.scoring.LeaderboardEntry
import navikt.appsec.securitychampionapp.app.scoring.OwnSeasonScore
import navikt.appsec.securitychampionapp.app.scoring.RecognitionEntry
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(ScoringController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class ScoringControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var scoringService: ScoringService

    @MockitoBean
    lateinit var participantRepository: ParticipantStore

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should expose names and ranks without points in recognition`() {
        mockAuthenticatedUser(USER_ROLE)
        whenever(scoringService.recognition()).thenReturn(listOf(RecognitionEntry("Person", 1)))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/recognition"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].fullName").value("Person"))
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[0].points").doesNotExist())
    }

    @Test
    fun `should deny the full leaderboard to employees who are not participants`() {
        mockAuthenticatedUser(USER_ROLE)
        whenever(participantRepository.findByNavNoEmail("user@nav.no"))
            .thenReturn(null)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/leaderboard"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should allow admins to view exact leaderboard scores`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val participantId = UUID.randomUUID()
        whenever(participantRepository.findByNavNoEmail("user@nav.no"))
            .thenReturn(participant(participantId))
        whenever(scoringService.leaderboard(participantId))
            .thenReturn(listOf(LeaderboardEntry("Person", 1, 25, "Novice", true)))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/leaderboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].points").value(25))
            .andExpect(jsonPath("$[0].isCurrentUser").value(true))
    }

    @Test
    fun `should allow an active participant to view exact leaderboard scores`() {
        mockAuthenticatedUser(USER_ROLE)
        val participantId = UUID.randomUUID()
        whenever(participantRepository.findByNavNoEmail("user@nav.no"))
            .thenReturn(participant(participantId))
        whenever(scoringService.leaderboard(participantId))
            .thenReturn(listOf(LeaderboardEntry("Person", 1, 25, "Novice", true)))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/leaderboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].points").value(25))
            .andExpect(jsonPath("$[0].isCurrentUser").value(true))
    }

    @Test
    fun `should expose current season score and personal rank to an active participant`() {
        val participantId = UUID.randomUUID()
        mockAuthenticatedUser(USER_ROLE)
        whenever(participantRepository.findByNavNoEmail("user@nav.no"))
            .thenReturn(participant(participantId))
        whenever(scoringService.ownScore(participantId)).thenReturn(
            OwnSeasonScore(
                season = SeasonSummary(
                    id = UUID.randomUUID(),
                    startsOn = LocalDate.parse("2026-01-01"),
                    endsOn = null,
                    nextResetDate = LocalDate.parse("2027-01-01"),
                ),
                points = 25,
                level = "Novice",
                rank = 4,
                tiers = navikt.appsec.securitychampionapp.app.scoring.defaultScoringConfiguration.tiers,
            )
        )

        mockMvc.perform(MockMvcRequestBuilders.get("/api/scoring/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.points").value(25))
            .andExpect(jsonPath("$.level").value("Novice"))
            .andExpect(jsonPath("$.rank").value(4))
    }

    private fun mockAuthenticatedUser(role: String) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val filterChain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("user@nav.no", "A12345"),
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

    private fun participant(id: UUID) =
        ProgramParticipant(
            id = id,
            navNoEmail = "user@nav.no",
            navIdent = "A12345",
            email = "user@nav.no",
            fullname = "Person",
            teams = emptyList(),
            status = ParticipationStatus.ACTIVE,
            createdAt = "2026-01-01T00:00:00Z",
        )
}
