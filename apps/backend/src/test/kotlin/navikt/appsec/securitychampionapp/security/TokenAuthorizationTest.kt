package navikt.appsec.securitychampionapp.security

import navikt.appsec.securitychampionapp.app.api.AdminController
import navikt.appsec.securitychampionapp.app.api.Controller
import navikt.appsec.securitychampionapp.app.events.EventCatalogService
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.MemberRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.EventQueryResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantQueryResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.security.dto.TokenResponse
import navikt.appsec.securitychampionapp.utils.Validate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.Base64

@WebMvcTest(
    Controller::class,
    AdminController::class,
    properties = ["spring.security.token-validation.groups=test-admin-group"],
)
@Import(SecurityConfig::class, TokenIntrospection::class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class TokenAuthorizationTest {
    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
            .build()
    }

    @MockitoBean
    lateinit var tokenClient: TokenValidationClient

    @MockitoBean
    lateinit var participantRepository: ProgramParticipantRepository

    @MockitoBean
    lateinit var memberRepository: MemberRepository

    @MockitoBean
    lateinit var eventRepository: EventRepository

    @MockitoBean
    lateinit var eventCatalogService: EventCatalogService

    @MockitoBean
    lateinit var validate: Validate

    @MockitoBean
    lateinit var teamCatalog: TeamCatalog

    @Test
    fun `should reject Swagger credentials on application admin endpoints`() {
        whenever(participantRepository.findAllParticipants())
            .thenReturn(ProgramParticipantQueryResponse(isOk = true))
        val credentials = Base64.getEncoder().encodeToString("admin:test123".toByteArray())

        mockMvc.perform(
            get("/api/admin/participants")
                .header("Authorization", "Basic $credentials")
        ).andExpect(status().isUnauthorized)

        verify(tokenClient, never()).validate(any(), any(), any())
        verify(participantRepository, never()).findAllParticipants()
    }

    @Test
    fun `should allow admin access for a validated token with the configured group`() {
        whenever(tokenClient.validate(any(), any(), any())).thenReturn(validToken())
        whenever(participantRepository.findAllParticipants())
            .thenReturn(ProgramParticipantQueryResponse(isOk = true))

        mockMvc.perform(
            get("/api/admin/participants").header("Authorization", "Bearer test-token")
        ).andExpect(status().isOk)

        verify(participantRepository).findAllParticipants()
    }

    @Test
    fun `should deny admin access for a validated token without the configured group`() {
        whenever(tokenClient.validate(any(), any(), any()))
            .thenReturn(validToken().copy(groups = listOf("another-group")))

        mockMvc.perform(
            get("/api/admin/participants").header("Authorization", "Bearer test-token")
        ).andExpect(status().isForbidden)

        verify(participantRepository, never()).findAllParticipants()
    }

    @Test
    fun `should deny admin access when group claims are unavailable`() {
        whenever(tokenClient.validate(any(), any(), any()))
            .thenReturn(validToken().copy(groups = emptyList()))

        mockMvc.perform(
            get("/api/admin/participants").header("Authorization", "Bearer test-token")
        ).andExpect(status().isForbidden)

        verify(participantRepository, never()).findAllParticipants()
    }

    @Test
    fun `should deny access for an inactive token or a validation error`() {
        whenever(tokenClient.validate(any(), any(), any()))
            .thenReturn(
                validToken().copy(active = false),
                validToken().copy(error = "validation_failed"),
            )

        repeat(2) {
            mockMvc.perform(
                get("/api/admin/participants").header("Authorization", "Bearer test-token")
            ).andExpect(status().isUnauthorized)
        }

        verify(participantRepository, never()).findAllParticipants()
    }

    @Test
    fun `should deny access when token validation is unavailable without reusing previous authorization`() {
        whenever(tokenClient.validate(any(), any(), any()))
            .thenReturn(validToken())
            .thenThrow(IllegalStateException("Introspection unavailable"))
        whenever(participantRepository.findAllParticipants())
            .thenReturn(ProgramParticipantQueryResponse(isOk = true))

        mockMvc.perform(
            get("/api/admin/participants").header("Authorization", "Bearer test-token")
        ).andExpect(status().isOk)
        mockMvc.perform(
            get("/api/admin/participants").header("Authorization", "Bearer test-token")
        ).andExpect(status().isUnauthorized)

        verify(participantRepository).findAllParticipants()
    }

    @Test
    fun `should deny access when preferred username is missing`() {
        whenever(tokenClient.validate(any(), any(), any()))
            .thenReturn(validToken().copy(preferredUsername = null))

        mockMvc.perform(
            get("/api/admin/participants").header("Authorization", "Bearer test-token")
        ).andExpect(status().isUnauthorized)

        verify(participantRepository, never()).findAllParticipants()
    }

    @Test
    fun `should allow event requests when preferred username exists without nav no email`() {
        whenever(tokenClient.validate(any(), any(), any())).thenReturn(validToken())
        whenever(eventCatalogService.getAllEvents())
            .thenReturn(EventQueryResponse(isOk = true, queryResult = emptyList()))

        mockMvc.perform(
            get("/api/events").header("Authorization", "Bearer test-token")
        ).andExpect(status().isOk)

        verify(eventCatalogService).getAllEvents()
    }

    private fun validToken() = TokenResponse(
        active = true,
        preferredUsername = "admin@example.test",
        ident = "TEST123",
        groups = listOf("test-admin-group"),
        error = null,
    )
}
