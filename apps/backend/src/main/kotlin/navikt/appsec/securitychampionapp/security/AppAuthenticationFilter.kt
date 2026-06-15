package navikt.appsec.securitychampionapp.security

import org.springframework.web.filter.OncePerRequestFilter

abstract class AppAuthenticationFilter : OncePerRequestFilter()
