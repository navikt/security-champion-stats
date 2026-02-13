package navikt.appsec.securitychampionstats.common.security.azure

import navikt.appsec.securitychampionstats.common.security.client.TokenValidationClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val tokenClient: TokenValidationClient,
    @Value($$"${spring.security.token-validation.identity-provider}") private val identityProvider: String,
    @Value($$"${spring.security.token-validation.url}") private val url: String,
    @Value($$"${spring.security.token-validation.groups}") private val groupId: String,
) {

    @Bean
    fun filerChain(http: HttpSecurity): SecurityFilterChain {
        val introspectionFilter = TokenIntrospection(
            tokenClient,
            url,
            identityProvider,
            groupId,
        )

        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/**", "/actuator/health").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(introspectionFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}