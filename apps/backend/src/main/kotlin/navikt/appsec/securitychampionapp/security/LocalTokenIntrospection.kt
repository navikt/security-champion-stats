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
    // TODO: Add possibility to test with local user and local admin user
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
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
        SecurityContextHolder.getContext().authentication = authentication
        filterChain.doFilter(request, response)
        logger.debug("Completed token introspection successfully for local request: ${request.requestURI}")
    }
}