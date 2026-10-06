package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminDeltaEligibleCategoryController
import navikt.appsec.securitychampionapp.app.scoring.DeltaCategoryNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.DeltaEligibleCategory
import navikt.appsec.securitychampionapp.app.scoring.DeltaEligibleCategoryService
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

@WebMvcTest(AdminDeltaEligibleCategoryController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminDeltaEligibleCategoryControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: DeltaEligibleCategoryService

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @Test
    fun `should add an eligible Delta category as an admin`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(service.addCategory(7, "admin@nav.no"))
            .thenReturn(DeltaEligibleCategory(7, "Security Champions", Instant.parse("2026-10-05T10:00:00Z")))

        mockMvc.perform(post(7)).andExpect(status().isCreated)
    }

    @Test
    fun `should reject an unknown Delta category`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(service.addCategory(7, "admin@nav.no")).thenThrow(DeltaCategoryNotFoundException())

        mockMvc.perform(post(7)).andExpect(status().isBadRequest)
    }

    @Test
    fun `should reject a Delta category that is already eligible`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(service.addCategory(7, "admin@nav.no")).thenThrow(DuplicateKeyException("conflict"))

        mockMvc.perform(post(7)).andExpect(status().isConflict)
    }

    @Test
    fun `should reject eligible category changes for non-admins`() {
        mockAuthenticatedUser(USER_ROLE)

        mockMvc.perform(post(7)).andExpect(status().isForbidden)
    }

    @Test
    fun `should remove an eligible Delta category and return not found when missing`() {
        mockAuthenticatedUser(ADMIN_ROLE)
        whenever(service.removeCategory(7, "admin@nav.no")).thenReturn(true)
        whenever(service.removeCategory(8, "admin@nav.no")).thenReturn(false)

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/delta/eligible-categories/7"))
            .andExpect(status().isNoContent)
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/delta/eligible-categories/8"))
            .andExpect(status().isNotFound)
    }

    private fun post(categoryId: Int) =
        MockMvcRequestBuilders.post("/api/admin/delta/eligible-categories")
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content("""{"deltaCategoryId":$categoryId}""")

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
