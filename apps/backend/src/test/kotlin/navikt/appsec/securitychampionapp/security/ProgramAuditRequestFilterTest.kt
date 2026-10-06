package navikt.appsec.securitychampionapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramAuditRepository
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class ProgramAuditRequestFilterTest {
    private val auditService = mock<ProgramAuditService>()
    private val auditRepository = mock<ProgramAuditRepository>()
    private val beanFactory = StaticListableBeanFactory(
        mapOf(
            ProgramAuditService::class.java.name to auditService,
            ProgramAuditRepository::class.java.name to auditRepository,
            tools.jackson.databind.ObjectMapper::class.java.name to JsonMapper.builder().build(),
        )
    )
    private val filter = ProgramAuditRequestFilter(
        beanFactory.getBeanProvider(ProgramAuditService::class.java),
        beanFactory.getBeanProvider(ProgramAuditRepository::class.java),
        beanFactory.getBeanProvider(tools.jackson.databind.ObjectMapper::class.java),
    )

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should capture deletion actor linkage before the operation erases the target`() {
        authenticatedAs("deleted-admin@nav.no")
        val actor = AuditRunContext(
            actorNavNoEmail = "deleted-admin@nav.no",
            actorParticipantId = UUID.fromString(PARTICIPANT_ID),
        )
        whenever(auditService.captureRunContext("deleted-admin@nav.no")).thenReturn(actor)
        val request = MockHttpServletRequest(
            "DELETE",
            "/api/admin/participants/$PARTICIPANT_ID",
        ).apply {
            servletPath = "/api/admin/participants/$PARTICIPANT_ID"
            setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/admin/participants/{id}")
            setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("id" to PARTICIPANT_ID))
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, servletResponse ->
            verify(auditService).captureRunContext("deleted-admin@nav.no")
            (servletResponse as HttpServletResponse).status = HttpServletResponse.SC_NO_CONTENT
        })

        verify(auditService).recordParticipantDeletion(actor, UUID.fromString(PARTICIPANT_ID))
        verify(auditService, never()).recordAdminRequest(
            eq("DELETE"),
            eq("/api/admin/participants/{id}"),
            eq(HttpServletResponse.SC_NO_CONTENT),
            eq("deleted-admin@nav.no"),
            eq(UUID.fromString(PARTICIPANT_ID)),
        )
    }

    @Test
    fun `should link a mapping attempt to its participant without storing the request body`() {
        authenticatedAs("admin@nav.no")
        val request = MockHttpServletRequest("POST", "/api/admin/slack/mappings").apply {
            servletPath = "/api/admin/slack/mappings"
            setContent("""{"slackUserId":"U_PRIVATE","participantId":"$PARTICIPANT_ID"}""".toByteArray())
            setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/admin/slack/mappings")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { servletRequest, servletResponse ->
            servletRequest.inputStream.readAllBytes()
            (servletResponse as HttpServletResponse).status = HttpServletResponse.SC_CONFLICT
        })

        verify(auditService).recordAdminRequest(
            "POST",
            "/api/admin/slack/mappings",
            HttpServletResponse.SC_CONFLICT,
            "admin@nav.no",
            UUID.fromString(PARTICIPANT_ID),
        )
    }

    @Test
    fun `should sanitize raw path values before recording a rejected request`() {
        authenticatedAs("admin@nav.no")
        val request = MockHttpServletRequest("POST", "/api/admin/member/private-person@nav.no").apply {
            servletPath = "/api/admin/member/private-person@nav.no"
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, servletResponse ->
            (servletResponse as HttpServletResponse).status = HttpServletResponse.SC_BAD_REQUEST
        })

        verify(auditService).recordAdminRequest(
            "POST",
            "/api/admin/member/{id}",
            HttpServletResponse.SC_BAD_REQUEST,
            "admin@nav.no",
            null,
        )
    }

    @Test
    fun `should record an unhandled failure without changing the exception`() {
        authenticatedAs("admin@nav.no")
        val request = MockHttpServletRequest("POST", "/api/admin/events").apply {
            servletPath = "/api/admin/events"
        }
        val response = MockHttpServletResponse()

        assertThrows<IllegalStateException> {
            filter.doFilter(request, response, FilterChain { _, _ -> throw IllegalStateException("operation failed") })
        }

        verify(auditService).recordAdminRequest(
            "POST",
            "/api/admin/events",
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "admin@nav.no",
            null,
        )
    }

    @Test
    fun `should link authorization rejections to a known participant without using raw route data`() {
        authenticatedAs("non-admin@nav.no")
        val request = MockHttpServletRequest("PUT", "/api/admin/participants/$PARTICIPANT_ID/status").apply {
            servletPath = "/api/admin/participants/$PARTICIPANT_ID/status"
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, servletResponse ->
            (servletResponse as HttpServletResponse).status = HttpServletResponse.SC_FORBIDDEN
        })

        verify(auditService).recordAdminRequest(
            "PUT",
            "/api/admin/participants/{id}/status",
            HttpServletResponse.SC_FORBIDDEN,
            "non-admin@nav.no",
            UUID.fromString(PARTICIPANT_ID),
        )
    }

    private fun authenticatedAs(email: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            AppPrincipal(email, "A12345"),
            null,
            emptyList(),
        )
    }

    private companion object {
        const val PARTICIPANT_ID = "00000000-0000-0000-0000-000000000001"
    }
}
