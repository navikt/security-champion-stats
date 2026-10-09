package navikt.appsec.securitychampionapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.config.writeProblemDetail
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.http.HttpStatus
import tools.jackson.databind.ObjectMapper

@Component
@Profile("!local")
class TokenIntrospection(
    private val tokenClient: TokenValidationClient,
    @Value($$"${spring.security.token-validation.identity-provider}") private val identityProvider: String,
    @Value($$"${spring.security.token-validation.url}") private val url: String,
    @Value($$"${spring.security.token-validation.groups}") private val id: String,
    private val objectMapper: ObjectMapper,
): AppAuthenticationFilter() {

    init {
        require(id.isNotBlank()) { "Admin group configuration must not be blank" }
    }

    private val log = LoggerFactory.getLogger(TokenIntrospection::class.java)
    private val publicPaths = listOf(
        "/auth",
        "/actuator/health",
        "/swagger-ui",
        "/swagger-ui.html",
        "/v3/api-docs"
    )
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath

        return publicPaths.any { path.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.getHeader("Authorization")?.trim()
        if (token.isNullOrEmpty() || !token.startsWith("Bearer ", ignoreCase = true)) {
            handleUnauthenticated(request, response)
            return
        }
        val rawToken = token.substringAfter(" ").trim()
        if (rawToken.isEmpty()) {
            handleUnauthenticated(request, response)
            return
        }

        try {
            val result = tokenClient.validate(url, rawToken, identityProvider)

            if (!result.active || result.error != null) {
                log.warn("Token is inactive for request: ${request.requestURI}")
                handleUnauthenticated(request, response)
                return
            }
            
            val navIdent = result.ident
            if (navIdent.isNullOrEmpty()) {
                log.warn("Missing NAVident claim in token for request: ${request.requestURI}")
                handleUnauthenticated(request, response)
                return
            }

            val preferredUsername = result.preferredUsername
            if (preferredUsername.isNullOrEmpty()) {
                log.warn("Missing preferred_username claim in token for request: ${request.requestURI}")
                handleUnauthenticated(request, response)
                return
            }
            val groups = result.groups

            val authorities =
                if (groups.contains(id)) {
                    listOf(SimpleGrantedAuthority("ROLE_$ADMIN_ROLE"))
                } else {
                    listOf(SimpleGrantedAuthority("ROLE_$USER_ROLE"))
                }

            val principal = AppPrincipal(preferredUsername, navIdent)
            val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            log.error("Token validation failed due to error: $e")
            handleUnauthenticated(request, response)
        }
    }

    private fun handleUnauthenticated(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val accept = request.getHeader("Accept") ?: ""
        val wantsHtml = accept.contains("text/html", ignoreCase = true)

        if (wantsHtml) {
            response.status = 302
        } else {
            writeProblemDetail(
                response,
                request,
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required",
                objectMapper,
            )
        }
    }
}
