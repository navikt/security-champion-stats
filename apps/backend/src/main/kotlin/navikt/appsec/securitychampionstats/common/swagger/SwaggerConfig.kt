package navikt.appsec.securitychampionstats.common.swagger

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
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
}