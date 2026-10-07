package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminEventClaimController
import navikt.appsec.securitychampionapp.app.api.EventClaimController
import navikt.appsec.securitychampionapp.app.events.EventClaimService
import navikt.appsec.securitychampionapp.app.events.EventClaimException
import navikt.appsec.securitychampionapp.app.events.EventClaimFailure
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(EventClaimController::class, AdminEventClaimController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class EventClaimControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: EventClaimService
    @MockitoBean lateinit var introspectionFilter: AppAuthenticationFilter

    private fun authenticate(role: String?) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val chain = invocation.getArgument<FilterChain>(2)
            if (role != null) SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("user@nav.no", "T12345"), null, listOf(SimpleGrantedAuthority("ROLE_$role")),
            )
            try { chain.doFilter(request, response) } finally { SecurityContextHolder.clearContext() }
            null
        }.`when`(introspectionFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any())
    }

    @Test
    fun `ordinary users cannot list or review administrator claims`() {
        authenticate("USER")
        mvc.perform(get("/api/admin/event-claims")).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        mvc.perform(post("/api/admin/event-claims/00000000-0000-0000-0000-000000000001/reviews")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `unauthenticated employees cannot read claims`() {
        authenticate(null)
        mvc.perform(get("/api/event-claims")).andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        verifyNoInteractions(service)
    }

    @Test
    fun `malformed submissions are problem details rather than internal errors`() {
        authenticate("USER")
        mvc.perform(post("/api/event-claims").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @CsvSource("INVALID,400", "FORBIDDEN,403", "CONFLICT,409", "NOT_FOUND,404")
    fun `claim failures map to problem details`(failure: EventClaimFailure, expectedStatus: Int) {
        authenticate("USER")
        whenever(service.overview("user@nav.no", false)).thenThrow(EventClaimException(failure, "A safe claim detail"))
        mvc.perform(get("/api/event-claims"))
            .andExpect(status().`is`(expectedStatus))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("A safe claim detail"))
    }
}
