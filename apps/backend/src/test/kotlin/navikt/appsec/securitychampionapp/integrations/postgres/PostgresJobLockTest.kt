package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresJobLockTest {
    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var flyway: Flyway

    @BeforeAll
    fun setUp() {
        dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
        }
        jdbcTemplate = JdbcTemplate(dataSource)
        flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
    }

    @AfterAll
    fun tearDown() {
        dataSource.close()
    }

    @BeforeEach
    fun resetDatabase() {
        flyway.clean()
        flyway.migrate()
    }

    @Test
    fun `should skip a sequential scheduled duplicate until its interval expires`() {
        val jobLock = PostgresJobLock(dataSource)
        var executions = 0

        jobLock.runWithLockAtMostOncePerInterval(1_006L, "importPlaybookEvents", Duration.ofHours(6)) {
            executions++
        }
        jobLock.runWithLockAtMostOncePerInterval(1_006L, "importPlaybookEvents", Duration.ofHours(6)) {
            executions++
        }

        assertThat(executions).isEqualTo(1)

        jdbcTemplate.update(
            "UPDATE scheduled_job_runs SET last_started_at = last_started_at - INTERVAL '6 hours 1 second'",
        )

        jobLock.runWithLockAtMostOncePerInterval(1_006L, "importPlaybookEvents", Duration.ofHours(6)) {
            executions++
        }

        assertThat(executions).isEqualTo(2)
    }
}
