package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminDeltaCategoriesController
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategory
import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategorySource
import navikt.appsec.securitychampionapp.integrations.delta.DeltaFailure
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminDeltaCategoriesController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminDeltaCategoriesControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var categorySource: DeltaCategorySource

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should return Delta categories to an admin`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(categorySource.categories()).thenReturn(listOf(DeltaCategory(7, "Security")))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/delta/categories"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""[{"id":7,"name":"Security"}]"""))
    }

    @Test
    fun `should deny Delta categories to non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/delta/categories"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should return a sanitized unavailable response when Delta categories cannot be fetched`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(categorySource.categories()).thenThrow(DeltaIntegrationException(DeltaFailure.API))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/delta/categories"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(DeltaFailure.API.summary))
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
