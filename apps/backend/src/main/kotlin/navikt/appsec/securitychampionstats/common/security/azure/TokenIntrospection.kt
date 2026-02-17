package navikt.appsec.securitychampionstats.common.security.azure

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
        log.info("Starting token introspection for request: ${request.requestURI}")
        val token = request.getHeader("Authorization")?.trim()
        log.info("Received request for path: ${request.requestURI} with Authorization header: ${token?.take(20)}...")
        if (token.isNullOrEmpty() || !token.startsWith("Bearer ", ignoreCase = true)) {
            handleUnauthenticated(request, response, "missing_or_invalid_authorization_header")
            return
        }

        log.info("Extracted token: ${token.take(10)}... from Authorization header")
        if (token.isEmpty()) {
            handleUnauthenticated(request, response, "empty_token")
            return
        }

        log.info("Received request with token: ${token.take(10)}... for path: ${request.requestURI}")

        try {
            val result = tokenClient.validate(naisUrl, token, identityProvider)
            log.info("Token introspection response for request ${request.requestURI}: active=${result.active}, claims=${result.claims.keys.joinToString(",")}")
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
            log.info("Token validated successfully for user: $preferredUsername with NAVident: $navIdent")
            val group = result.claims["groups"]?.jsonPrimitive?.content

            val authorities =
                if (!group.isNullOrEmpty() && group.contains(id)) {
                    listOf(SimpleGrantedAuthority("ROLE_Admin"))
                } else {
                    listOf(SimpleGrantedAuthority("ROLE_User"))
                }
            log.info("Assigned authorities for user $preferredUsername: ${authorities.joinToString { it.authority }}")
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