package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.Controller
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.app.events.EventCatalogService
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantQueryResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantUpdateResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
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

@WebMvcTest(Controller::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class ControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var participantRepository: ProgramParticipantRepository

    @MockitoBean
    lateinit var eventCatalogService: EventCatalogService

    @MockitoBean
    lateinit var teamCatalog: TeamCatalog

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    private fun mockAuthenticatedUser() {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val filterChain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("user@nav.no", "A12345"),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
            try {
                filterChain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }

            @Test
            fun `should return date only playbook events from the combined catalog`() {
                mockAuthenticatedUser()
                val event = navikt.appsec.securitychampionapp.app.api.dto.Event(
                    id = "playbook:test", name = "Course", description = "Alle",
                    startDate = "2026-10-20", endDate = "2026-10-22", location = "",
                    type = "event", deltaEvent = false, allDay = true, link = "https://example.org",
                )
                whenever(eventCatalogService.getAllEvents()).thenReturn(
                    navikt.appsec.securitychampionapp.integrations.postgress.dto.EventQueryResponse(true, listOf(event))
                )
                mockMvc.perform(MockMvcRequestBuilders.get("/api/events"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].id").value("playbook:test"))
                    .andExpect(jsonPath("$[0].allDay").value(true))
                    .andExpect(jsonPath("$[0].startDate").value("2026-10-20"))
            }
            null
        }.`when`(introspectionFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any())
    }

    @Test
    fun `should enroll an authenticated employee by nav no email`() {
        mockAuthenticatedUser()
        whenever(participantRepository.findByNavNoEmail("user@nav.no"))
            .thenReturn(ProgramParticipantQueryResponse(isOk = true))
        whenever(teamCatalog.fetchAllMembersWithTeamData()).thenReturn(emptyList())
        whenever(participantRepository.enroll("user@nav.no", "A12345", "user@nav.no"))
            .thenReturn(ProgramParticipantUpdateResponse(isOk = true, affectedRows = 1))
        whenever(participantRepository.updateAuthenticatedIdentity("user@nav.no", "A12345", "user@nav.no"))
            .thenReturn(ProgramParticipantUpdateResponse(isOk = true, affectedRows = 1))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/enroll")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(status().isCreated)

        verify(participantRepository).enroll("user@nav.no", "A12345", "user@nav.no")
    }

    @Test
    fun `should not allow self enrollment to reactivate a deactivated participant`() {
        mockAuthenticatedUser()
        whenever(participantRepository.findByNavNoEmail("user@nav.no"))
            .thenReturn(
                ProgramParticipantQueryResponse(
                    isOk = true,
                    queryResult = listOf(
                        ProgramParticipant(
                            id = "00000000-0000-0000-0000-000000000001",
                            navNoEmail = "user@nav.no",
                            navIdent = "A12345",
                            email = "user@nav.no",
                            fullname = "User",
                            teams = emptyList(),
                            status = "DEACTIVATED",
                            createdAt = "2026-01-01T00:00:00Z",
                        )
                    ),
                )
            )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/enroll")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(status().isConflict)

        verify(participantRepository, never()).enroll(any(), any(), any(), any(), any())
    }

    @Test
    fun `should return active participant names and teams without exact points or email`() {
        mockAuthenticatedUser()
        whenever(participantRepository.findActiveParticipants())
            .thenReturn(
                ProgramParticipantQueryResponse(
                    isOk = true,
                    queryResult = listOf(
                        ProgramParticipant(
                            id = "00000000-0000-0000-0000-000000000001",
                            navNoEmail = "user@nav.no",
                            navIdent = "A12345",
                            email = "user@nav.no",
                            fullname = "User",
                            teams = listOf("Team"),
                            status = "ACTIVE",
                            createdAt = "2026-01-01T00:00:00Z",
                        )
                    ),
                )
            )

        mockMvc.perform(MockMvcRequestBuilders.get("/api/members"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].fullname").value("User"))
            .andExpect(jsonPath("$[0].teams[0]").value("Team"))
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].points").doesNotExist())
    }
}
