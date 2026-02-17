package navikt.appsec.securitychampionstats.common.security.azure

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import navikt.appsec.securitychampionstats.common.security.client.TokenValidationClient
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class TokenIntrospection(
    private val tokenClient: TokenValidationClient,
    private val naisUrl: String,
    private val identityProvider: String,
    private val id: String
): OncePerRequestFilter() {

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
            val result = tokenClient.validate(naisUrl, rawToken, identityProvider)
            if (!result.active) {
                log.warn("Token is inactive for request: ${request.requestURI}")
                handleUnauthenticated(request, response, "inactive_token")
                return
            }

            val navIdent = result.claims["NAVident"]?.jsonPrimitive?.content
            if (navIdent.isNullOrEmpty()) {
                log.warn("Missing NAVident claim in token for request: ${request.requestURI}")
                handleUnauthenticated(request, response, "Missing NAVident")
                return
            }

            val preferredUsername = result.claims["preferred_username"]?.jsonPrimitive?.content
            if (preferredUsername.isNullOrEmpty()) {
                log.warn("Missing preferred_username claim in token for request: ${request.requestURI}")
                handleUnauthenticated(request, response, "Missing preferred Username")
                return
            }
            val groups = result.claims["groups"]?.jsonArray?.map { it.jsonPrimitive.content }

            val authorities =
                if (!groups.isNullOrEmpty() && groups.contains(id)) {
                    listOf(SimpleGrantedAuthority("ROLE_Admin"))
                } else {
                    listOf(SimpleGrantedAuthority("ROLE_User"))
                }
            val authentication = UsernamePasswordAuthenticationToken(preferredUsername, null, authorities)
            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
            log.info("Completed token introspection and request processing for user: $preferredUsername with authorities: ${authorities.joinToString { it.authority }}")
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