package navikt.appsec.securitychampionapp.testsupport

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate

/** Base class for tests that need a real, freshly migrated PostgreSQL schema. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PostgresIntegrationTest(private val maxPoolSize: Int = 4) {
    protected lateinit var dataSource: HikariDataSource
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeAll
    fun openDataSource() {
        dataSource = PostgresTestDatabase.dataSource(maxPoolSize)
        jdbcTemplate = JdbcTemplate(dataSource)
    }

    @AfterAll
    fun closeDataSource() {
        dataSource.close()
    }

    @BeforeEach
    fun resetDatabase() {
        PostgresTestDatabase.reset()
    }
}
