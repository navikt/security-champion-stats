package navikt.appsec.securitychampionapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramAuditRepository
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.util.AntPathMatcher
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class ProgramAuditRequestFilter(
    private val auditServiceProvider: ObjectProvider<ProgramAuditService>,
    private val auditRepositoryProvider: ObjectProvider<ProgramAuditRepository>,
    private val objectMapperProvider: ObjectProvider<ObjectMapper>,
) : OncePerRequestFilter() {
    private val auditLogger = LoggerFactory.getLogger(ProgramAuditRequestFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!request.servletPath.startsWith("/api/admin/") || request.method !in STATE_CHANGING_METHODS) {
            filterChain.doFilter(request, response)
            return
        }

        val deletionActor = if (request.method == "DELETE" &&
            AntPathMatcher().match("/api/admin/participants/{id}", request.servletPath)
        ) {
            val principal = SecurityContextHolder.getContext().authentication?.principal as? AppPrincipal
            auditServiceProvider.ifAvailable?.captureRunContext(principal?.email)
        } else {
            null
        }
        val pathSlackTarget = resolveSlackMappingTargetBeforeRemoval(request)
        val cachingRequest = if (
            request.method == "POST" && request.servletPath == "/api/admin/slack/mappings"
        ) {
            ContentCachingRequestWrapper(request, 16_384)
        } else {
            null
        }
        var operationFailed = false
        try {
            filterChain.doFilter(cachingRequest ?: request, response)
        } catch (e: Exception) {
            operationFailed = true
            throw e
        } finally {
            auditServiceProvider.ifAvailable?.let { auditService ->
                val routeTemplate = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
                    ?: KNOWN_ROUTES.firstOrNull { AntPathMatcher().match(it, request.servletPath) }
                val participantId = participantIdFromTemplate(request, routeTemplate)
                    ?: pathSlackTarget
                    ?: cachingRequest?.let(::participantIdFromMappingBody)
                val actor = SecurityContextHolder.getContext().authentication?.principal as? AppPrincipal
                val isSuccessfulDeletion = request.method == "DELETE" &&
                    routeTemplate == "/api/admin/participants/{id}" &&
                    response.status in 200..299 && !operationFailed

                if (isSuccessfulDeletion) {
                    auditService.recordParticipantDeletion(deletionActor, participantId)
                } else {
                    auditService.recordAdminRequest(
                        method = request.method,
                        routeTemplate = routeTemplate,
                        httpStatus = if (operationFailed) 500 else response.status,
                        actorNavNoEmail = actor?.email,
                        targetParticipantId = participantId,
                    )
                }
            }
        }
    }

    private fun participantIdFromTemplate(request: HttpServletRequest, routeTemplate: String?): UUID? {
        if (routeTemplate?.contains("/participants/{id}") != true) return null
        val values = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
            ?: AntPathMatcher().extractUriTemplateVariables(routeTemplate, request.servletPath)
        return (values["id"] as? String)?.toUuid()
    }

    private fun resolveSlackMappingTargetBeforeRemoval(request: HttpServletRequest): UUID? {
        if (request.method != "DELETE" ||
            !request.servletPath.startsWith("/api/admin/slack/mappings/")
        ) {
            return null
        }
        val slackUserId = request.servletPath.removePrefix("/api/admin/slack/mappings/")
        if (slackUserId.isBlank() || slackUserId.contains('/')) return null
        return try {
            auditRepositoryProvider.ifAvailable?.participantIdForSlackUserId(slackUserId)
        } catch (e: Exception) {
            auditLogger.error("Could not resolve Slack mapping target for audit capture: {}", e.javaClass.simpleName)
            null
        }
    }

    private fun participantIdFromMappingBody(request: ContentCachingRequestWrapper): UUID? =
        try {
            val body = request.contentAsByteArray
            val values: Map<*, *>? = objectMapperProvider.ifAvailable?.readValue(body, Map::class.java)
            (values?.get("participantId") as? String)?.toUuid()
        } catch (e: Exception) {
            auditLogger.warn(
                "Could not resolve Slack mapping participant target for audit capture: {}",
                e.javaClass.simpleName,
            )
            null
        }

    private fun String.toUuid(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        val STATE_CHANGING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        val KNOWN_ROUTES = listOf(
            "/api/admin/participants/{id}/status",
            "/api/admin/participants/{id}",
            "/api/admin/scoring/participants/{id}/adjustments",
            "/api/admin/scoring/season/reset-date",
            "/api/admin/scoring/season/reset",
            "/api/admin/slack/mappings/{slackUserId}",
            "/api/admin/slack/mappings",
            "/api/admin/slack/sync",
            "/api/admin/delta/sync",
            "/api/admin/delta/events/sync",
            "/api/admin/playbook/events/sync",
            "/api/admin/delta/event-mappings/{id}",
            "/api/admin/delta/event-mappings",
            "/api/admin/delta/eligible-categories/{categoryId}",
            "/api/admin/delta/eligible-categories",
            "/api/admin/member/attended/{email}",
            "/api/admin/member/{id}",
            "/api/admin/member",
            "/api/admin/events",
        )
    }
}
