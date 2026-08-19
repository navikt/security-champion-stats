package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminController
import navikt.appsec.securitychampionapp.app.api.dto.AddMember
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.DatabaseUpdateResponse
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.utils.Validate
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper


@WebMvcTest(AdminController::class)
@Import(SecurityConfig::class)
class AdminControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var repo: PostgresRepository

    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

    @MockitoBean
    lateinit var validate: Validate

    private fun mockAuthenticatedUser(role: String) {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<ServletRequest>(0)
            val response = invocation.getArgument<ServletResponse>(1)
            val filterChain = invocation.getArgument<FilterChain>(2)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                "admin@nav.no",
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
        whenever(repo.addMember(any(), any(), any(), any())).thenReturn(DatabaseUpdateResponse(isOk = true))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/member")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(AddMember(fullName = "Test User", email = "test@nav.no")))
        ).andExpect(status().isCreated)
            .andExpect(content().string("User was created"))
    }
}