package navikt.appsec.securitychampionapp.config

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramAuditRepository
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.ProgramAuditRequestFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.http.HttpStatus
import tools.jackson.databind.ObjectMapper


const val ADMIN_ROLE = "ADMIN"
const val USER_ROLE = "USER"

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val introspectionFilter: AppAuthenticationFilter,
    private val auditServiceProvider: ObjectProvider<ProgramAuditService>,
    private val auditRepositoryProvider: ObjectProvider<ProgramAuditRepository>,
    private val objectMapperProvider: ObjectProvider<ObjectMapper>,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, _ ->
                    writeProblemDetail(
                        response,
                        request,
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        "Authentication is required",
                        objectMapperProvider.getObject(),
                    )
                }
                it.accessDeniedHandler { request, response, _ ->
                    writeProblemDetail(
                        response,
                        request,
                        HttpStatus.FORBIDDEN,
                        "Forbidden",
                        "You are not allowed to access this resource",
                        objectMapperProvider.getObject(),
                    )
                }
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/auth/**",
                    "/actuator/health",
                    "/internal/local-auth/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                it.requestMatchers("/api/admin/**").hasRole(ADMIN_ROLE)
                it.anyRequest().authenticated()
            }
            .addFilterBefore(introspectionFilter, BasicAuthenticationFilter::class.java )
            .addFilterAfter(
                ProgramAuditRequestFilter(
                    auditServiceProvider,
                    auditRepositoryProvider,
                    objectMapperProvider,
                ),
                BasicAuthenticationFilter::class.java,
            )
            .build()
    }
}
