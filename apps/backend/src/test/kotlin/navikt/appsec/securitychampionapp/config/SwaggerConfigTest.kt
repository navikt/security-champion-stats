package navikt.appsec.securitychampionapp.config

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
}
