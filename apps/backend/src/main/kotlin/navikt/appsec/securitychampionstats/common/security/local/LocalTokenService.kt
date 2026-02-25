package navikt.appsec.securitychampionstats.common.security.local

import navikt.appsec.securitychampionstats.common.security.dto.IntrospectionRequest
import navikt.appsec.securitychampionstats.common.security.local.dto.LocalTokenClaims
import navikt.appsec.securitychampionstats.common.security.local.dto.LocalTokenRequest
import navikt.appsec.securitychampionstats.common.security.local.dto.LocalTokenResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Profile("local")
@Service
class LocalTokenService(
    @Value("\${spring.security.token-validation.identity-provider}")
    private val identityProvider: String
) {
    private val tokens = ConcurrentHashMap<String, LocalTokenClaims>()

    fun issueToken(request: LocalTokenRequest): LocalTokenResponse {
        val navIdent = request.navIdent.trim()
        val preferredUsername = request.preferredUsername.trim()
        val groups = request.groups.map { it.trim() }.filter { it.isNotEmpty() }

        validate(navIdent, preferredUsername, groups)

        val token = UUID.randomUUID().toString()
        tokens[token] = LocalTokenClaims(navIdent, preferredUsername, groups)
        return LocalTokenResponse(token)
    }

    fun introspect(request: IntrospectionRequest): Map<String, Any> {
        if (request.identity_provider != identityProvider) {
            return inactive()
        }

        val claims = tokens[request.token] ?: return inactive()
        return mapOf(
            "active" to true,
            "NAVident" to claims.navIdent,
            "preferred_username" to claims.preferredUsername,
            "groups" to claims.groups
        )
    }

    private fun validate(navIdent: String, preferredUsername: String, groups: List<String>) {
        if (navIdent.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "navIdent is required")
        }
        if (preferredUsername.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "preferredUsername is required")
        }
        if (groups.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "groups must contain at least one value")
        }
    }

    private fun inactive(): Map<String, Any> = mapOf("active" to false)
}

