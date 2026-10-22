package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminFeedbackController
import navikt.appsec.securitychampionapp.app.api.FeedbackController
import navikt.appsec.securitychampionapp.app.feedback.*
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

@WebMvcTest(FeedbackController::class, AdminFeedbackController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class FeedbackControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: FeedbackService
    @MockitoBean lateinit var introspectionFilter: AppAuthenticationFilter
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")

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
    fun `participant submission returns only a receipt and uses authenticated identity`() {
        authenticate("USER")
        whenever(service.submit(eq("user@nav.no"), any())).thenReturn(FeedbackReceipt(id))
        mvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("""{"id":"$id","title":"Suggestion","description":"Improve the app"}"""))
            .andExpect(status().isCreated)
            .andExpect(content().json("""{"id":"$id"}""", org.springframework.test.json.JsonCompareMode.STRICT))
    }

    @Test
    fun `ordinary participants cannot list inspect or change feedback`() {
        authenticate("USER")
        mvc.perform(get("/api/admin/feedback")).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/feedback/$id")).andExpect(status().isForbidden)
        mvc.perform(post("/api/admin/feedback/$id/actions").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/feedback")).andExpect(status().isMethodNotAllowed)
        verifyNoInteractions(service)
    }

    @Test
    fun `anonymous submissions and inactive participants are rejected`() {
        authenticate(null)
        mvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
        authenticate("USER")
        whenever(service.submit(eq("user@nav.no"), any())).thenThrow(
            FeedbackException(FeedbackFailure.FORBIDDEN, "Active program membership is required"),
        )
        mvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("""{"id":"$id","title":"Suggestion","description":"Improve the app"}"""))
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `administrators can search and conflicts use problem details`() {
        authenticate("ADMIN")
        whenever(service.search(FeedbackSearch(query = "menu", owner = "mine"), "user@nav.no"))
            .thenReturn(FeedbackPage(emptyList(), null, "user@nav.no"))
        mvc.perform(get("/api/admin/feedback").param("q", "menu").param("owner", "mine"))
            .andExpect(status().isOk).andExpect(jsonPath("$.items").isEmpty)
        whenever(service.act(eq(id), eq("user@nav.no"), any())).thenThrow(
            FeedbackException(FeedbackFailure.CONFLICT, "The feedback changed. Reload it before continuing"),
        )
        mvc.perform(post("/api/admin/feedback/$id/actions").contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":1,"action":"CLAIM"}"""))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("The feedback changed. Reload it before continuing"))
    }
}
