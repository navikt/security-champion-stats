package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminDeltaEventMappingController
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMapping
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMappingService
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(AdminDeltaEventMappingController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminDeltaEventMappingControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: DeltaEventMappingService

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should add an explicit Delta event mapping as an admin`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val mapping = DeltaEventMapping(
            id = UUID.randomUUID(),
            programEventName = "Security Champion meetup",
            deltaEventUuid = UUID.randomUUID(),
            createdAt = Instant.parse("2026-10-05T10:00:00Z"),
        )
        whenever(service.addMapping("Security Champion meetup", mapping.deltaEventUuid.toString(), "admin@nav.no"))
            .thenReturn(mapping)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/delta/event-mappings")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """{"programEventName":"Security Champion meetup","deltaEventUuid":"${mapping.deltaEventUuid}"}"""
                )
        ).andExpect(status().isCreated)
    }

    @Test
    fun `should reject Delta mapping changes for non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/delta/event-mappings")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """{"programEventName":"Security Champion meetup","deltaEventUuid":"123e4567-e89b-12d3-a456-426614174000"}"""
                )
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `should reject a Delta event UUID that is already mapped`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        val deltaEventUuid = UUID.randomUUID()
        whenever(
            service.addMapping(
                eq("Security Champion meetup"),
                eq(deltaEventUuid.toString()),
                eq("admin@nav.no"),
            )
        ).thenThrow(DuplicateKeyException("mapping conflict"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/delta/event-mappings")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("""{"programEventName":"Security Champion meetup","deltaEventUuid":"$deltaEventUuid"}""")
        ).andExpect(status().isConflict)
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
