package navikt.appsec.securitychampionapp.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType as OpenApiMediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse as OpenApiApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
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
                    .description(
                        "API for Security Champion Stats. Paths and schemas are generated from the Spring MVC " +
                            "controllers. All API requests require an Entra ID bearer token; administrator " +
                            "operations also require membership in the configured Entra group. Errors use " +
                            "RFC 9457 Problem Details."
                    )
                    .contact(Contact().name("AppSec Team"))
                    .license(License().name("MIT License").url("https://opensource.org/licenses/MIT"))
            )
            .addSecurityItem(SecurityRequirement().addList("Bearer Auth"))
            .components(
                Components()
                    .addSecuritySchemes("Bearer Auth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Entra ID token; administrator access requires the configured Entra group")
                    )
            )

    @Bean
    open fun problemDetailResponses(): OpenApiCustomizer =
        OpenApiCustomizer { api ->
            api.components
                .addSchemas("ProblemDetail", problemDetailSchema())
                .addResponses(
                    "Unauthorized",
                    problemResponse("Authentication is required"),
                )
                .addResponses(
                    "Forbidden",
                    problemResponse("The authenticated user is not allowed to access this resource"),
                )
                .addResponses(
                    "InternalServerError",
                    problemResponse("The request could not be completed"),
                )
                .addResponses(
                    "ProblemDetailsError",
                    problemResponse("The request failed"),
                )

            api.paths.values
                .flatMap { pathItem: PathItem -> pathItem.readOperations() }
                .forEach { operation ->
                    operation.responses
                        .addApiResponse(
                            "401",
                            OpenApiApiResponse().`$ref`("#/components/responses/Unauthorized"),
                        )
                        .addApiResponse(
                            "403",
                            OpenApiApiResponse().`$ref`("#/components/responses/Forbidden"),
                        )
                        .addApiResponse(
                            "500",
                            OpenApiApiResponse().`$ref`("#/components/responses/InternalServerError"),
                        )
                        .addApiResponse(
                            "default",
                            OpenApiApiResponse().`$ref`("#/components/responses/ProblemDetailsError"),
                        )
                }
        }

    private fun problemResponse(detail: String): OpenApiApiResponse =
        OpenApiApiResponse()
            .description(detail)
            .content(
                Content().addMediaType(
                    "application/problem+json",
                    OpenApiMediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ProblemDetail")),
                )
            )

    private fun problemDetailSchema(): Schema<Any> =
        Schema<Any>()
            .type("object")
            .description("RFC 9457 Problem Details response")
            .addProperty("type", Schema<String>().type("string").format("uri"))
            .addProperty("title", Schema<String>().type("string"))
            .addProperty("status", Schema<Int>().type("integer").format("int32"))
            .addProperty("detail", Schema<String>().type("string"))
            .addProperty("instance", Schema<String>().type("string").format("uri-reference"))
            .addRequiredItem("type")
            .addRequiredItem("title")
            .addRequiredItem("status")
            .addRequiredItem("detail")
}