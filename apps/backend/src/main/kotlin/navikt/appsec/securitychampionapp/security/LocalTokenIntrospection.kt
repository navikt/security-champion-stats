package navikt.appsec.securitychampionapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.writeProblemDetail
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import navikt.appsec.securitychampionapp.security.dto.TokenResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.http.HttpStatus
import tools.jackson.databind.ObjectMapper

@Component
@Profile("local")
class LocalTokenIntrospection(
    private val objectMapper: ObjectMapper,
) : AppAuthenticationFilter() {

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
            writeProblemDetail(
                response,
                request,
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required",
                objectMapper,
            )
            return
        }
        val result = TokenResponse(
            active = true,
            ident = "A1234544426",
            preferredUsername = "local.user@nav.no",
            groups = listOf("local-admin-group", "local-user-group"),
            error = null
        )
        val principal = AppPrincipal(result.preferredUsername!!, result.ident!!)
        val authentication = UsernamePasswordAuthenticationToken(
            principal, null, listOf(SimpleGrantedAuthority("ROLE_$ADMIN_ROLE"))
        )
        SecurityContextHolder.getContext().authentication = authentication
        filterChain.doFilter(request, response)
    }

    private fun isSwaggerPath(requestPath: String): Boolean {
        return SWAGGER_PATHS.any { requestPath.contains(it) }
    }
}