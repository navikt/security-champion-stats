package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import org.assertj.core.api.Assertions
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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRepositoryTest {

    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

    }

    lateinit var dataSource: HikariDataSource
    lateinit var jdbcTemplate: JdbcTemplate
    lateinit var repository: PostgresRepository
    lateinit var flyway: Flyway

    @BeforeAll
    fun setupRepository() {
        dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 2
        }
        jdbcTemplate = JdbcTemplate(dataSource)
        repository = PostgresRepository(jdbcTemplate)
        flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
    }

    @AfterAll
    fun closeDataSource() {
        dataSource.close()
    }

    @BeforeEach
    fun setup() {
        flyway.clean()
        flyway.migrate()
    }

    @Test
    fun `should read member including level from flyway migrated schema`() {
        insertMember(email = "test@nav.no", level = "2", teams = listOf("team-a", "team-b"))

        val member = repository.getMemberByEmail("test@nav.no")

        Assertions.assertThat(member.isOk).isTrue()
        Assertions.assertThat(member.queryResult).hasSize(1)
        val members = member.queryResult.orEmpty()
        Assertions.assertThat(members.first().level).isEqualTo("2")
        Assertions.assertThat(members.first().teams).isEqualTo(listOf("team-a", "team-b"))
    }

    @Test
    fun `should delete member when email exists`() {
        insertMember(id = "member-1", email = "test@nav.no")
        insertMember(id = "member-2", email = "keep@nav.no")

        repository.deleteMember("member-1")

        Assertions.assertThat(repository.getMemberByEmail("test@nav.no").queryResult).isEmpty()
        Assertions.assertThat(repository.getMemberByEmail("keep@nav.no").queryResult).hasSize(1)
        Assertions.assertThat(memberCount()).isEqualTo(1)
    }

    @Test
    fun `should leave existing members untouched when deleting member that does not exist`() {
        insertMember(email = "existing@nav.no")

        repository.deleteMember("missing@nav.no")

        Assertions.assertThat(repository.getMemberByEmail("existing@nav.no").queryResult).hasSize(1)
        Assertions.assertThat(memberCount()).isEqualTo(1)
    }

    @Test
    fun `should add points and update timestamp when member exists`() {
        val memberId = "member-1"
        val originalUpdatedAt = Instant.parse("2026-01-01T00:00:00Z")
        insertMember(id = memberId, email = "test@nav.no", points = 5, updatedAt = originalUpdatedAt)

        repository.addPoints(memberId, 10, "2")

        val updatedMember = repository.getMemberByEmail("test@nav.no")
        Assertions.assertThat(updatedMember.isOk).isTrue()
        Assertions.assertThat(updatedMember.queryResult!!.first().points).isEqualTo(15)
        Assertions.assertThat(fetchUpdatedAt("test@nav.no")).isAfter(originalUpdatedAt)
    }

    @Test
    fun `should accumulate points across multiple updates`() {
        val memberId = "member-1"
        insertMember(id = memberId, email = "test@nav.no", points = 3)

        repository.addPoints(memberId, 10, "2")
        repository.addPoints(memberId, 7, "2")

        Assertions.assertThat(repository.getMemberByEmail("test@nav.no").queryResult!!.first().points).isEqualTo(20)
    }

    @Test
    fun `should reset all member points and levels`() {
        val originalUpdatedAt = Instant.parse("2026-01-01T00:00:00Z")
        insertMember(email = "first@nav.no", points = 40, level = "4", updatedAt = originalUpdatedAt)
        insertMember(email = "second@nav.no", points = 10, level = "2", updatedAt = originalUpdatedAt)

        val updatedRows = repository.resetAllPointsAndLevels()

        Assertions.assertThat(updatedRows.isOk).isTrue()
        Assertions.assertThat(repository.getMemberByEmail("first@nav.no").queryResult!!.first().points).isEqualTo(0)
        Assertions.assertThat(repository.getMemberByEmail("first@nav.no").queryResult!!.first().level).isEqualTo("1")
        Assertions.assertThat(repository.getMemberByEmail("second@nav.no").queryResult!!.first().points).isEqualTo(0)
        Assertions.assertThat(repository.getMemberByEmail("second@nav.no").queryResult!!.first().level).isEqualTo("1")
        Assertions.assertThat(fetchUpdatedAt("first@nav.no")).isAfter(originalUpdatedAt)
        Assertions.assertThat(fetchUpdatedAt("second@nav.no")).isAfter(originalUpdatedAt)
    }

    @Test
    fun `should not change existing members when adding points to member that does not exist`() {
        insertMember(email = "existing@nav.no", points = 4)

        repository.addPoints("missing-id", 10, "2")

        Assertions.assertThat(repository.getMemberByEmail("existing@nav.no").queryResult!!.first().points).isEqualTo(4)
        Assertions.assertThat(memberCount()).isEqualTo(1)
    }

    private fun insertMember(
        id: String = UUID.randomUUID().toString(),
        fullname: String = "Test User",
        email: String,
        points: Int = 0,
        inProgram: Boolean = false,
        level: String = "1",
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt: Instant = createdAt,
        teams: List<String> = emptyList()
    ) {
        dataSource.connection.use { connection ->
            val teamsArray = connection.createArrayOf("text", teams.toTypedArray())
            connection.prepareStatement(
                """
                    INSERT INTO Members (
                        id,
                        fullname,
                        points,
                        create_at,
                        update_at,
                        email,
                        inProgram,
                        level,
                        teams
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, id)
                statement.setString(2, fullname)
                statement.setInt(3, points)
                statement.setObject(4, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                statement.setObject(5, OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                statement.setString(6, email)
                statement.setBoolean(7, inProgram)
                statement.setString(8, level)
                statement.setArray(9, teamsArray)
                statement.executeUpdate()
            }
        }
    }

    private fun memberCount(): Int {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Members", Int::class.java) ?: 0
    }

    private fun fetchUpdatedAt(email: String): Instant {
        return jdbcTemplate.queryForObject(
            "SELECT update_at FROM Members WHERE email = ?",
            OffsetDateTime::class.java,
            email
        )!!.toInstant()
    }
}
