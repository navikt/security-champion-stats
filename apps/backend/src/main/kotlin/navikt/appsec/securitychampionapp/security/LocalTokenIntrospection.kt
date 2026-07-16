package navikt.appsec.securitychampionapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.security.dto.TokenResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
@Profile("local")
class LocalTokenIntrospection : AppAuthenticationFilter() {
    private val SWAGGER_PATHS = setOf(
        "/swagger-ui",
        "/v3/api-docs",
        "/swagger-resources"
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestPath = request.requestURI

        if (isSwaggerPath(requestPath)) {
            val authentication = UsernamePasswordAuthenticationToken(
                "local-swagger-user", null, listOf(SimpleGrantedAuthority("ROLE_$ADMIN_ROLE"))
            )
            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
            return
        }

        val token = request.getHeader("Authorization")?.trim()
        if (token.isNullOrEmpty() || !token.startsWith("Bearer ", ignoreCase = true)) {
            response.sendError(401, "Missing or invalid authorization header")
            return
        }
        val result = TokenResponse(
            active = true,
            ident = "LZ00001",
            preferredUsername = "local.user@nav.no",
            groups = listOf("local-admin-group", "local-user-group"),
            error = null
        )

        val authentication = UsernamePasswordAuthenticationToken(
            result.preferredUsername!!, result.ident, listOf(SimpleGrantedAuthority("ROLE_$ADMIN_ROLE"))
        )
        logger.info("Completed token introspection successfully for local request: ${request.requestURI}")
        SecurityContextHolder.getContext().authentication = authentication
        filterChain.doFilter(request, response)
    }

    private fun isSwaggerPath(requestPath: String): Boolean {
        return SWAGGER_PATHS.any { requestPath.contains(it) }
    }
}