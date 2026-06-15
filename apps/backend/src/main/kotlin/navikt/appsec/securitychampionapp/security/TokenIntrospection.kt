package navikt.appsec.securitychampionapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.USER_ROLE
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
@Profile("!local")
class TokenIntrospection(
    @Value($$"${spring.security.token-validation.identity-provider}") private val identityProvider: String,
    @Value($$"${spring.security.token-validation.url}") private val url: String,
    @Value($$"${spring.security.token-validation.groups}") private val id: String,
): AppAuthenticationFilter() {

    private val tokenClient = TokenValidationClient()
    private val log = LoggerFactory.getLogger(TokenIntrospection::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.getHeader("Authorization")?.trim()
        if (token.isNullOrEmpty() || !token.startsWith("Bearer ", ignoreCase = true)) {
            handleUnauthenticated(request, response, "missing_or_invalid_authorization_header")
            return
        }
        val rawToken = token.substringAfter(" ").trim()
        if (rawToken.isEmpty()) {
            handleUnauthenticated(request, response, "empty_token")
            return
        }

        try {
            val result = tokenClient.validate(url, rawToken, identityProvider)

            if (!result.active || result.error != null) {
                log.warn("Token is inactive for request: ${request.requestURI}")
                handleUnauthenticated(request, response, "inactive_token")
                return
            }

            val navIdent = result.ident
            if (navIdent.isNullOrEmpty()) {
                log.warn("Missing NAVident claim in token for request: ${request.requestURI}")
                handleUnauthenticated(request, response, "Missing NAVident")
                return
            }

            val preferredUsername = result.preferredUsername
            if (preferredUsername.isNullOrEmpty()) {
                log.warn("Missing preferred_username claim in token for request: ${request.requestURI}")
                handleUnauthenticated(request, response, "Missing preferred Username")
                return
            }
            val groups = result.groups

            val authorities =
                if (groups.contains(id)) {
                    listOf(SimpleGrantedAuthority("ROLE_$ADMIN_ROLE"))
                } else {
                    listOf(SimpleGrantedAuthority("ROLE_$USER_ROLE"))
                }
            val authentication = UsernamePasswordAuthenticationToken(preferredUsername, navIdent, authorities)
            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
            log.debug("Completed token introspection successfully for request: ${request.requestURI}")
        } catch (e: Exception) {
            log.error("Token validation failed due to error: $e")
            handleUnauthenticated(request, response, "validation_error")
        }
    }

    private fun handleUnauthenticated(
        request: HttpServletRequest,
        response: HttpServletResponse,
        reason: String
    ) {
        val accept = request.getHeader("Accept") ?: ""
        val wantsHtml = accept.contains("text/html", ignoreCase = true)

        if (wantsHtml) {
            response.status = 302
        } else {
            response.status = 401
            response.contentType = "application/json"
            response.writer.write("""{"error":"unauthorized", "reason":"$reason"}""")
        }
    }
}
