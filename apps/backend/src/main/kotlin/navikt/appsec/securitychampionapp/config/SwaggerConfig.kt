package navikt.appsec.securitychampionapp.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class SwaggerConfig {

    @Bean
    open fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Security Champion Stats API")
                    .version("1.0")
                    .description("API for managing Security Champion statistics")
                    .contact(Contact().name("AppSec Team"))
                    .license(License().name("MIT License").url("https://opensource.org/licenses/MIT"))
            )
            .addSecurityItem(SecurityRequirement().addList("Basic Auth"))
            .components(
                Components()
                    .addSecuritySchemes("Basic Auth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("basic")
                            .description("Basic authentication for Swagger endpoints")
                    )
            )
}