package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminController
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.doNothing
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


@WebMvcTest(AdminController::class)
@Import(SecurityConfig::class)
class AdminControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc
    @MockitoBean
    lateinit var repo: PostgresRepository
    @MockitoBean
    lateinit var introspectionFilter: AppAuthenticationFilter

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
    fun `Trying access admin endpoint without admin role should return forbidden`() {
        mockAuthenticatedUser("USER")

        doNothing().`when`(repo).addMember(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyList<String>()
        )

        val request = """
            {
              "fullname": "Test User",
              "email": "test@nav.no"
            }
        """.trimIndent()

        val result = mockMvc.perform (
            MockMvcRequestBuilders.post("/api/admin/member")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(request)
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(403)
    }

    @Test
    fun `Trying access admin endpoint with admin role should return ok`() {
        mockAuthenticatedUser(ADMIN_ROLE)

        doNothing().`when`(repo).addMember(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyList<String>()
        )
        val request = """
            {
              "fullname": "Test User",
              "email": "test@nav.no"
            }
        """.trimIndent()
        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/api/admin/member")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(request)
        ).andReturn()
        val error = result.resolvedException
        assert(error == null)
        assert(result.response.status == 201)
        assert(result.response.contentAsString.contains("User was created"))
    }
}