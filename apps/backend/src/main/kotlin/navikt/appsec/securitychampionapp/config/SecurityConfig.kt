package navikt.appsec.securitychampionapp.config

import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter


const val ADMIN_ROLE = "ADMIN"
const val USER_ROLE = "USER"

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val introspectionFilter: AppAuthenticationFilter,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/**", "/actuator/health", "/internal/local-auth/**").permitAll()
                it.requestMatchers("/api/admin/**").hasRole(ADMIN_ROLE)
                it.anyRequest().authenticated()
            }
            .addFilterBefore(introspectionFilter, BasicAuthenticationFilter::class.java )
            .build()
    }
}
