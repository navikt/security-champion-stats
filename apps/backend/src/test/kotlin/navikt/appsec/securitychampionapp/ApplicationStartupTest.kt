package navikt.appsec.securitychampionapp

import navikt.appsec.securitychampionapp.integrations.slack.ChannelMembershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.test.util.ReflectionTestUtils
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Testcontainers
class ApplicationStartupTest {
    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    @Test
    fun `should start production application using main configuration`() {
        SpringApplicationBuilder(Application::class.java)
            .run(
                "--spring.config.location=file:src/main/resources/application.yaml",
                "--spring.profiles.active=prod",
                "--server.port=0",
                "--NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_JDBC_URL=${postgres.jdbcUrl}",
                "--NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_USERNAME=${postgres.username}",
                "--NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_PASSWORD=${postgres.password}",
                "--NAIS_TOKEN_INTROSPECTION_ENDPOINT=http://localhost/introspect",
                "--SLACK_TOKEN=synthetic-slack-token",
                "--SLACK_SC_CHANNEL_ID=test-sc-channel",
                "--SLACK_APPSEC_CHANNEL_ID=test-appsec-channel",
                "--SLACK_APPSEC_ACTIVITY_CHANNEL_ID=test-activity-channel",
                "--SLACK_USER_GROUP_ID=test-user-group",
            ).use { context ->
                assertThat(context.environment.activeProfiles).containsExactly("prod")
                val membershipService = context.getBean(ChannelMembershipService::class.java)
                assertThat(ReflectionTestUtils.getField(membershipService, "scChannelId"))
                    .isEqualTo("test-activity-channel")
                assertThat(ReflectionTestUtils.getField(membershipService, "userGrouping"))
                    .isEqualTo("test-user-group")

                val port = context.environment.getRequiredProperty("local.server.port")
                HttpClient.newHttpClient().use { client ->
                    val request = HttpRequest.newBuilder(URI("http://localhost:$port/v3/api-docs")).GET().build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.body()).contains("\"openapi\"")
                }
            }
    }
}
