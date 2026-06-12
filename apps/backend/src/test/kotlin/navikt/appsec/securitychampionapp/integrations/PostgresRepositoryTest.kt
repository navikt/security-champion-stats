package navikt.appsec.securitychampionapp.integrations

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit


@SpringBootTest
@Testcontainers
class PostgresRepositoryTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate
    lateinit var repository: PostgresRepository

    @BeforeEach
    fun setup() {
        repository = PostgresRepository(jdbcTemplate)
    }

    @Test
    fun `Check that query of getAllMembersInProgram is correct`() {
        repository.getAllMembersInProgram()
    }

    @Test
    fun `Check that query of getAllMembers is correct`() {
        repository.getAllMembers()
    }

    @Test
    fun `Check that query of addMember is correct`() {
        repository.addMember(
            fullname = "test tester",
            id = "test-id",
            email = "test@nav.no",
            teams = listOf("test-team")
        )
    }

    @Test
    fun `Check that query of getMemberByEmail is correct`() {
        repository.getMemberByEmail("test@nav.no")
    }

    @Test
    fun `Check that query of deleteMember is correct`() {
        repository.deleteMember("test@nav.no")
    }

    @Test
    fun `Check that query of addPoints is correct`() {
        repository.addPoints(
            email = "test@nav.no",
            points = 10
        )
    }

    @Test
    fun `Check that query of updateInProgram is correct`() {
        repository.updateInProgram(
            email = "test@nav.no",
            inProgram = true
        )
    }

    @Test
    fun `Check that queries of getSCAmountOverTime is correct`() {
        repository.getSCAmountOverTime()
        repository.getSCAmountOverTime(
            startDate = Instant.now(),
            endDate = Instant.now().plus(1, ChronoUnit.DAYS)
        )
    }
}