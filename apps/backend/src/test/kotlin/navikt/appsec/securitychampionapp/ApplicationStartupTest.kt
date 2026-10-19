package navikt.appsec.securitychampionapp

import navikt.appsec.securitychampionapp.app.jobs.SlackScoringSyncJob
import navikt.appsec.securitychampionapp.config.SlackMembershipProperties
import navikt.appsec.securitychampionapp.integrations.slack.SlackApiService
import org.assertj.core.api.Assertions.assertThat
import navikt.appsec.securitychampionapp.testsupport.PostgresTestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.test.util.ReflectionTestUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ApplicationStartupTest {
    private lateinit var database: PostgresTestDatabase.IsolatedDatabase

    @BeforeEach
    fun createDatabase() {
        database = PostgresTestDatabase.isolated(maxPoolSize = 2)
    }

    @AfterEach
    fun dropDatabase() {
        database.close()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `should start production application using main configuration`(membershipEnabled: Boolean) {
        SpringApplicationBuilder(Application::class.java)
            .run(
                "--spring.config.location=file:src/main/resources/application.yaml",
                "--spring.profiles.active=prod",
                "--server.port=0",
                "--NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_JDBC_URL=${database.jdbcUrl}",
                "--NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_USERNAME=${database.username}",
                "--NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_PASSWORD=${database.password}",
                "--NAIS_TOKEN_INTROSPECTION_ENDPOINT=http://localhost/introspect",
                "--SLACK_TOKEN=synthetic-slack-token",
                "--SLACK_SC_CHANNEL_ID=test-sc-channel",
                "--SLACK_MEMBERSHIP_ENABLED=$membershipEnabled",
                "--SLACK_MEMBERSHIP_WELCOME_CHANNEL_ID=C_WELCOME",
                "--SLACK_MEMBERSHIP_ADMIN_CHANNEL_ID=C_ADMIN",
                "--SLACK_MEMBERSHIP_USERGROUP_ID=S_GROUP",
                "--slack.membership.cron=-",
            ).use { context ->
                assertThat(context.environment.activeProfiles).containsExactly("prod")
                assertThat(context.getBean(SlackApiService::class.java)).isNotNull()
                val membership = context.getBean(SlackMembershipProperties::class.java)
                assertThat(membership.enabled).isEqualTo(membershipEnabled)
                assertThat(membership.dryRun).isTrue()
                assertThat(membership.welcomeChannelId).isEqualTo("C_WELCOME")
                assertThat(membership.adminChannelId).isEqualTo("C_ADMIN")
                assertThat(membership.usergroupId).isEqualTo("S_GROUP")
                val slackJob = context.getBean(SlackScoringSyncJob::class.java)
                assertThat(ReflectionTestUtils.getField(slackJob, "channelId"))
                    .isEqualTo("test-sc-channel")

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
