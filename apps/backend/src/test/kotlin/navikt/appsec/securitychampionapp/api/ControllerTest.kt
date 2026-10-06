package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.Controller
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
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
    lateinit var auditService: ProgramAuditService

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
        mockParticipant("ACTIVE")
        val enrolled = participantRepository.findByNavNoEmail("user@nav.no")
        whenever(participantRepository.findByNavNoEmail("user@nav.no"))
            .thenReturn(ProgramParticipantQueryResponse(isOk = true), enrolled)
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
        verify(auditService).recordParticipantEvent(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "PARTICIPANT_ENROLLED",
            "user@nav.no",
            details = mapOf("status" to "ACTIVE"),
        )
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
    fun `should allow a voluntary leaver to rejoin without creating another participant`() {
        mockAuthenticatedUser()
        mockParticipant("LEFT")
        whenever(participantRepository.rejoin("user@nav.no"))
            .thenReturn(ProgramParticipantUpdateResponse(isOk = true, affectedRows = 1))

        mockMvc.perform(MockMvcRequestBuilders.post("/api/enroll"))
            .andExpect(status().isOk)

        verify(participantRepository).rejoin("user@nav.no")
        verify(participantRepository, never()).enroll(any(), any(), any(), any(), any())
        verify(auditService).recordParticipantEvent(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "PARTICIPANT_REJOINED",
            "user@nav.no",
            details = mapOf("status" to "ACTIVE"),
        )
    }

    @Test
    fun `should leave using the authenticated identity only`() {
        mockAuthenticatedUser()
        mockParticipant("ACTIVE")
        whenever(participantRepository.leave("user@nav.no"))
            .thenReturn(ProgramParticipantUpdateResponse(isOk = true, affectedRows = 1))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"other@nav.no"}""")
        ).andExpect(status().isNoContent)

        verify(participantRepository).leave("user@nav.no")
        verify(auditService).recordParticipantEvent(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "PARTICIPANT_LEFT",
            "user@nav.no",
            details = mapOf("status" to "LEFT"),
        )
    }

    @Test
    fun `should reject departure of an administrator deactivated participant`() {
        mockAuthenticatedUser()
        mockParticipant("DEACTIVATED")

        mockMvc.perform(MockMvcRequestBuilders.post("/api/leave"))
            .andExpect(status().isConflict)
        verify(participantRepository, never()).leave(any())
        org.mockito.kotlin.verifyNoInteractions(auditService)
    }

    @Test
    fun `should make repeated voluntary departure idempotent`() {
        mockAuthenticatedUser()
        mockParticipant("LEFT")

        mockMvc.perform(MockMvcRequestBuilders.post("/api/leave"))
            .andExpect(status().isNoContent)
        verify(participantRepository, never()).leave(any())
        org.mockito.kotlin.verifyNoInteractions(auditService)
    }

    @Test
    fun `should report a concurrent administrator deactivation during rejoin`() {
        mockAuthenticatedUser()
        mockParticipant("LEFT")
        whenever(participantRepository.rejoin("user@nav.no"))
            .thenReturn(ProgramParticipantUpdateResponse(isOk = true, affectedRows = 0))

        mockMvc.perform(MockMvcRequestBuilders.post("/api/enroll"))
            .andExpect(status().isConflict)
        org.mockito.kotlin.verifyNoInteractions(auditService)
    }

    @Test
    fun `should surface a failed departure rather than report success`() {
        mockAuthenticatedUser()
        mockParticipant("ACTIVE")
        whenever(participantRepository.leave("user@nav.no"))
            .thenReturn(ProgramParticipantUpdateResponse(isOk = false, error = "Unavailable"))

        mockMvc.perform(MockMvcRequestBuilders.post("/api/leave"))
            .andExpect(status().isInternalServerError)
    }

    private fun mockParticipant(participationStatus: String) {
        whenever(participantRepository.findByNavNoEmail("user@nav.no")).thenReturn(
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
                        status = participationStatus,
                        createdAt = "2026-01-01T00:00:00Z",
                    )
                ),
            )
        )
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
