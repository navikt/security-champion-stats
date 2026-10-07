package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminController
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.api.dto.AddMember
import navikt.appsec.securitychampionapp.app.api.dto.DeleteParticipantRequest
import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.api.dto.UpdateParticipantStatusRequest
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.MemberRepository
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import navikt.appsec.securitychampionapp.utils.Validate
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DuplicateKeyException
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
import tools.jackson.databind.ObjectMapper


@WebMvcTest(AdminController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var repo: MemberRepository

    @MockitoBean
    lateinit var participantRepository: ParticipantStore

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @MockitoBean
    lateinit var eventRepository: EventRepository

    @MockitoBean
    lateinit var validate: Validate

    private fun mockAuthenticatedUser(role: String) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val filterChain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("admin@nav.no", "A12345"),
                null,
                listOf(SimpleGrantedAuthority("ROLE_$role"))
            )
            try {
                filterChain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }
            null
        }.`when`(introspectionFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any())
    }

    @Test
    fun `should reject invalid event input before persistence`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val event = testEvent()
        val invalidEvents = listOf(
            event.copy(name = "   "),
            event.copy(name = "a".repeat(101)),
            event.copy(location = "a".repeat(101)),
            event.copy(id = "not-a-uuid"),
            event.copy(startDate = "not-a-date"),
            event.copy(endDate = event.startDate),
            event.copy(endDate = "2026-11-01T08:00:00Z"),
            event.copy(type = "course"),
        )

        invalidEvents.forEach { invalidEvent ->
            mockMvc.perform(
                MockMvcRequestBuilders.post("/api/admin/events")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(invalidEvent))
            ).andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
        verify(eventRepository, never()).addEvent(any())
    }

    @Test
    fun `should return conflict for duplicate program events`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(eventRepository.addEvent(any())).thenThrow(DuplicateKeyException("Synthetic duplicate"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/events")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(testEvent()))
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("An event with this name, start time and location already exists"))
    }

    @Test
    fun `should return created event as JSON after saving`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val event = testEvent()

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/events")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(event))
        ).andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(event.id))
            .andExpect(jsonPath("$.name").value(event.name))
    }

    private fun testEvent() = Event(
        id = "00000000-0000-0000-0000-000000000001",
        name = "Security meetup",
        description = "Synthetic event",
        startDate = "2026-11-01T09:00:00Z",
        endDate = "2026-11-01T10:00:00Z",
        location = "Oslo",
        type = "meetup",
    )

    @Test
    fun `should return 403 when accessing admin endpoint without admin role`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/member")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(AddMember(fullName = "Test User", email = "test@nav.no")))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `should return 201 when accessing admin endpoint with admin role`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(validate.isValidEmail(any())).thenReturn(true)
        whenever(validate.isValidName(any())).thenReturn(true)
        whenever(repo.addMember(any(), any(), any(), any())).thenReturn(1)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/member")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(AddMember(fullName = "Test User", email = "test@nav.no")))
        ).andExpect(status().isCreated)
            .andExpect(content().string("User was created"))
    }

    @Test
    fun `should return a sanitized problem detail when member storage is unavailable`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(repo.getSCAmountOverTime())
            .thenThrow(DataAccessResourceFailureException("Synthetic database detail"))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/dashboard/members"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("The request could not be completed"))
    }

    @Test
    fun `should return 404 for the removed Slack test endpoint`() {
        mockAuthenticatedUser(ADMIN_ROLE)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/test/member/add/slack/test@nav.no"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should update participant status for admins`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(participantRepository.updateStatus(any(), eq(false), eq("admin@nav.no")))
            .thenReturn(1)

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/admin/participants/00000000-0000-0000-0000-000000000001/status")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(UpdateParticipantStatusRequest(active = false)))
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `should reject participant deletion without confirmation and reason`() {
        mockAuthenticatedUser(ADMIN_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api/admin/participants/00000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(
                    DeleteParticipantRequest(confirmed = false, reason = "Requested by employee")
                ))
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api/admin/participants/00000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(DeleteParticipantRequest(confirmed = true, reason = "")))
        ).andExpect(status().isBadRequest)

        verify(participantRepository, never()).permanentlyDelete(any())
    }

    @Test
    fun `should reject participant management for non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/admin/participants/00000000-0000-0000-0000-000000000001/status")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(UpdateParticipantStatusRequest(active = false)))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `should permanently delete participant after confirmation and reason`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(participantRepository.permanentlyDelete(any()))
            .thenReturn(1)

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api/admin/participants/00000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    objectMapper.writeValueAsString(
                        DeleteParticipantRequest(confirmed = true, reason = "Requested by employee")
                    )
                )
        ).andExpect(status().isNoContent)

        verify(participantRepository).permanentlyDelete(any())
    }
}