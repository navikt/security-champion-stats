package navikt.appsec.securitychampionapp.config.dto

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    var username: String = "admin",
    var password: String = ""
)