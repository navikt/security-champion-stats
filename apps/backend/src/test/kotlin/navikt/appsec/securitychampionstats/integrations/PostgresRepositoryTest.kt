package navikt.appsec.securitychampionstats.integrations

import navikt.appsec.securitychampionstats.integration.postgres.PostgresRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.jdbc.core.JdbcTemplate

@WebMvcTest(PostgresRepository::class)
@AutoConfigureMockMvc
class PostgresRepositoryTest {
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

}