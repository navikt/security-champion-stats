package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
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
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgramParticipantRepositoryTest {
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
    private lateinit var repository: ProgramParticipantRepository
    private lateinit var flyway: Flyway

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
        repository = ProgramParticipantRepository(jdbcTemplate)
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
    fun resetDatabase() {
        flyway.clean()
        flyway.migrate()
    }

    @Test
    fun `should enroll once per nav no email`() {
        val first = repository.enroll("user@nav.no", "A12345", "user@nav.no")
        val duplicate = repository.enroll("user@nav.no", "A12345", "user@nav.no")

        assertThat(first.isOk).isTrue()
        assertThat(first.affectedRows).isEqualTo(1)
        assertThat(duplicate.isOk).isTrue()
        assertThat(duplicate.affectedRows).isZero()
        assertThat(repository.findByNavNoEmail("user@nav.no").queryResult).hasSize(1)
    }

    @Test
    fun `should treat a newly issued nav no email as a distinct participant`() {
        repository.enroll("old@nav.no", "A12345", "user@nav.no")
        repository.enroll("new@nav.no", "A12345", "user@nav.no")

        val oldParticipant = repository.findByNavNoEmail("old@nav.no").queryResult.single()
        val newParticipant = repository.findByNavNoEmail("new@nav.no").queryResult.single()

        assertThat(UUID.fromString(oldParticipant.id)).isNotEqualTo(UUID.fromString(newParticipant.id))
        assertThat(repository.findAllParticipants().queryResult).hasSize(2)
    }

    @Test
    fun `should retain participant data when deactivating and reactivating`() {
        repository.enroll("user@nav.no", "A12345", "user@nav.no")
        val participant = repository.findByNavNoEmail("user@nav.no").queryResult.single()
        val id = UUID.fromString(participant.id)

        assertThat(repository.updateStatus(id, active = false, actorNavNoEmail = "admin@nav.no").affectedRows)
            .isEqualTo(1)
        assertThat(repository.findByNavNoEmail("user@nav.no").queryResult.single().status)
            .isEqualTo("DEACTIVATED")
        val audit = jdbcTemplate.queryForMap(
            """
                SELECT actor_nav_no_email, action,
                    before_values ->> 'status' AS before_status,
                    after_values ->> 'status' AS after_status
                FROM program_participant_audit
                WHERE participant_id = ?
            """.trimIndent(),
            id,
        )
        assertThat(audit["actor_nav_no_email"]).isEqualTo("admin@nav.no")
        assertThat(audit["action"]).isEqualTo("PARTICIPATION_STATUS_CHANGED")
        assertThat(audit["before_status"]).isEqualTo("ACTIVE")
        assertThat(audit["after_status"]).isEqualTo("DEACTIVATED")

        assertThat(repository.updateStatus(id, active = true, actorNavNoEmail = "admin@nav.no").affectedRows)
            .isEqualTo(1)
        assertThat(repository.findByNavNoEmail("user@nav.no").queryResult.single().status).isEqualTo("ACTIVE")
    }

    @Test
    fun `should permanently delete only the selected participant`() {
        repository.enroll("first@nav.no", "A12345", "first@nav.no")
        repository.enroll("second@nav.no", "A12346", "second@nav.no")
        val first = repository.findByNavNoEmail("first@nav.no").queryResult.single()
        val second = repository.findByNavNoEmail("second@nav.no").queryResult.single()
        jdbcTemplate.update(
            "INSERT INTO Members (id, fullname, email) VALUES (?, ?, ?)",
            "legacy-first",
            "First User",
            "first@nav.no",
        )
        repository.updateStatus(
            UUID.fromString(first.id),
            active = false,
            actorNavNoEmail = "first@nav.no",
        )
        repository.updateStatus(
            UUID.fromString(second.id),
            active = false,
            actorNavNoEmail = "first@nav.no",
        )

        val deletion = repository.permanentlyDelete(
            UUID.fromString(first.id),
        )

        assertThat(deletion.isOk).isTrue()
        assertThat(deletion.affectedRows).isEqualTo(1)
        assertThat(repository.findByNavNoEmail("first@nav.no").queryResult).isEmpty()
        assertThat(repository.findByNavNoEmail("second@nav.no").queryResult).hasSize(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM Members WHERE email = ?",
                Int::class.javaObjectType,
                "first@nav.no",
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM program_participant_audit WHERE participant_id = ?",
                Int::class.javaObjectType,
                UUID.fromString(first.id),
            )
        ).isZero()
        val remainingAudit = jdbcTemplate.queryForMap(
            "SELECT actor_nav_no_email FROM program_participant_audit WHERE participant_id = ?",
            UUID.fromString(second.id),
        )
        assertThat(remainingAudit["actor_nav_no_email"]).isNull()
    }

    @Test
    fun `should update profile details without changing participant status`() {
        repository.enroll("user@nav.no", "A12345", "user@nav.no")
        repository.updateStatus(
            UUID.fromString(repository.findByNavNoEmail("user@nav.no").queryResult.single().id),
            active = false,
            actorNavNoEmail = "admin@nav.no",
        )

        val update = repository.updateProfile(
            navIdent = "A12345",
            email = "user@nav.no",
            fullname = "Updated User",
            teams = listOf("Team A", "Team B"),
        )

        val participant = repository.findByNavNoEmail("user@nav.no").queryResult.single()
        assertThat(update.isOk).isTrue()
        assertThat(participant.fullname).isEqualTo("Updated User")
        assertThat(participant.teams).containsExactly("Team A", "Team B")
        assertThat(participant.status).isEqualTo("DEACTIVATED")
    }
}
