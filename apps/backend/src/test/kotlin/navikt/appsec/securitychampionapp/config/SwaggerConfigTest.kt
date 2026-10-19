package navikt.appsec.securitychampionapp.config

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityScheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SwaggerConfigTest {
    @Test
    fun `should advertise Entra bearer authentication rather than Basic authentication`() {
        val api = SwaggerConfig().openAPI()
        val scheme = api.components.securitySchemes.getValue("Bearer Auth")

        assertThat(scheme.type).isEqualTo(SecurityScheme.Type.HTTP)
        assertThat(scheme.scheme).isEqualTo("bearer")
        assertThat(api.security.single()).containsKey("Bearer Auth")
        assertThat(api.components.securitySchemes).doesNotContainKey("Basic Auth")
    }

    @Test
    fun `should document common authorization and server errors as problem details`() {
        val api = SwaggerConfig().openAPI()
        assertThat(api.info.description).contains("RFC 9457 Problem Details")
        api.paths = Paths().addPathItem(
            "/api/test",
            PathItem().get(
                Operation().responses(
                    ApiResponses().addApiResponse("200", ApiResponse()),
                ),
            ),
        )

        SwaggerConfig().problemDetailResponses().customise(api)

        val operation = requireNotNull(api.paths["/api/test"]?.get)
        assertThat(operation.responses.keys).contains("401", "403", "500", "default")
        assertThat(operation.responses["401"]?.`$ref`)
            .isEqualTo("#/components/responses/Unauthorized")
        assertThat(operation.responses["default"]?.`$ref`)
            .isEqualTo("#/components/responses/ProblemDetailsError")
        val problemDetail = requireNotNull(api.components.schemas["ProblemDetail"])
        assertThat(problemDetail.properties.keys)
            .contains("type", "title", "status", "detail", "instance")
        assertThat(problemDetail.required).contains("type", "title", "status", "detail")
        assertThat(requireNotNull(api.components.responses["Unauthorized"]).content)
            .containsKey("application/problem+json")
        assertThat(requireNotNull(api.components.responses["Forbidden"]).content)
            .containsKey("application/problem+json")
        assertThat(requireNotNull(api.components.responses["InternalServerError"]).content)
            .containsKey("application/problem+json")
        assertThat(requireNotNull(api.components.responses["ProblemDetailsError"]).content)
            .containsKey("application/problem+json")
    }
}
