package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
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

        assertThat(first).isEqualTo(1)
        assertThat(duplicate).isZero()
        assertThat(repository.findByNavNoEmail("user@nav.no")).isNotNull
        val participant = requireNotNull(repository.findByNavNoEmail("user@nav.no"))
        assertThat(repository.findById(participant.id)).isEqualTo(participant)
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    @Test
    fun `should treat a newly issued nav no email as a distinct participant`() {
        repository.enroll("old@nav.no", "A12345", "user@nav.no")
        repository.enroll("new@nav.no", "A12345", "user@nav.no")

        val oldParticipant = requireNotNull(repository.findByNavNoEmail("old@nav.no"))
        val newParticipant = requireNotNull(repository.findByNavNoEmail("new@nav.no"))

        assertThat(oldParticipant.id).isNotEqualTo(newParticipant.id)
        assertThat(repository.findAllParticipants()).hasSize(2)
    }

    @Test
    fun `should retain participant data when deactivating and reactivating`() {
        repository.enroll("user@nav.no", "A12345", "user@nav.no")
        val participant = requireNotNull(repository.findByNavNoEmail("user@nav.no"))
        val id = participant.id

        assertThat(repository.updateStatus(id, active = false, actorNavNoEmail = "admin@nav.no"))
            .isEqualTo(1)
        assertThat(repository.findByNavNoEmail("user@nav.no")?.status)
            .isEqualTo(ParticipationStatus.DEACTIVATED)
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

        assertThat(repository.updateStatus(id, active = true, actorNavNoEmail = "admin@nav.no"))
            .isEqualTo(1)
        assertThat(repository.findByNavNoEmail("user@nav.no")?.status).isEqualTo(ParticipationStatus.ACTIVE)
    }

    @Test
    fun `should retain credits through voluntary departure and rejoin without bypassing admin deactivation`() {
        repository.enroll("user@nav.no", "A12345", "user@nav.no")
        val id = requireNotNull(repository.findByNavNoEmail("user@nav.no")).id
        jdbcTemplate.update(
            """
                INSERT INTO activity_credits (
                    participant_id, season_id, credit_type, uniqueness_key, source_reference, points
                )
                SELECT ?, id, 'SLACK_WEEK', '2026-10-05', 'channel:timestamp', 1
                FROM program_seasons WHERE ends_on IS NULL
            """.trimIndent(),
            id,
        )

        assertThat(repository.leave("user@nav.no")).isEqualTo(1)
        assertThat(repository.leave("user@nav.no")).isZero()
        assertThat(repository.findByNavNoEmail("user@nav.no")?.status).isEqualTo(ParticipationStatus.LEFT)
        assertThat(repository.findActiveParticipants()).isEmpty()
        assertThat(repository.rejoin("user@nav.no")).isEqualTo(1)
        assertThat(repository.findActiveParticipants()).hasSize(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_credits", Int::class.java))
            .isEqualTo(1)

        repository.updateStatus(id, false, "admin@nav.no")
        assertThat(repository.rejoin("user@nav.no")).isZero()
        assertThat(repository.leave("user@nav.no")).isZero()
        assertThat(repository.findByNavNoEmail("user@nav.no")?.status)
            .isEqualTo(ParticipationStatus.DEACTIVATED)
    }

    @Test
    fun `should anonymize deleted administrator in retained adjustments and integration configuration`() {
        repository.enroll("admin@nav.no", "A12345", "admin@nav.no")
        repository.enroll("other@nav.no", "A12346", "other@nav.no")
        val adminId = requireNotNull(repository.findByNavNoEmail("admin@nav.no")).id
        val otherId = requireNotNull(repository.findByNavNoEmail("other@nav.no")).id
        jdbcTemplate.update(
            """
                INSERT INTO point_adjustments (
                    participant_id, season_id, points_delta, reason, actor_nav_no_email, score_before, score_after
                ) SELECT ?, id, 1, 'Correction', 'admin@nav.no', 0, 1
                FROM program_seasons WHERE ends_on IS NULL
            """.trimIndent(), otherId,
        )
        jdbcTemplate.update(
            """
                INSERT INTO slack_account_mappings (slack_user_id, participant_id, created_by_nav_no_email)
                VALUES ('U_OTHER', ?, 'admin@nav.no')
            """.trimIndent(), otherId,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_delta_event_mappings (
                    program_event_name, delta_event_uuid, created_by_nav_no_email
                ) VALUES ('Event', ?, 'admin@nav.no')
            """.trimIndent(), UUID.randomUUID(),
        )
        jdbcTemplate.update(
            """
                INSERT INTO delta_eligible_categories (category_id, category_name, created_by_nav_no_email)
                VALUES (1, 'Security', 'admin@nav.no')
            """.trimIndent(),
        )

        assertThat(repository.permanentlyDelete(adminId)).isEqualTo(1)
        for ((table, column) in listOf(
            "point_adjustments" to "actor_nav_no_email",
            "slack_account_mappings" to "created_by_nav_no_email",
            "program_delta_event_mappings" to "created_by_nav_no_email",
            "delta_eligible_categories" to "created_by_nav_no_email",
        )) {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)).isEqualTo(1)
            assertThat(jdbcTemplate.queryForMap("SELECT $column FROM $table")[column]).isNull()
        }
        assertThat(repository.findByNavNoEmail("other@nav.no")).isNotNull
    }

    @Test
    fun `should permanently delete only the selected participant`() {
        repository.enroll("first@nav.no", "A12345", "first@nav.no")
        repository.enroll("second@nav.no", "A12346", "second@nav.no")
        val first = requireNotNull(repository.findByNavNoEmail("first@nav.no"))
        val second = requireNotNull(repository.findByNavNoEmail("second@nav.no"))
        jdbcTemplate.update(
            "INSERT INTO Members (id, fullname, email) VALUES (?, ?, ?)",
            "legacy-first",
            "First User",
            "first@nav.no",
        )
        repository.updateStatus(
            first.id,
            active = false,
            actorNavNoEmail = "first@nav.no",
        )
        repository.updateStatus(
            second.id,
            active = false,
            actorNavNoEmail = "first@nav.no",
        )
        val firstId = first.id
        val secondId = second.id
        jdbcTemplate.update(
            """
                INSERT INTO program_audit_events (
                    id, action, outcome, actor_nav_no_email, target_participant_id, details
                ) VALUES (?, 'PARTICIPANT_LEFT', 'SUCCEEDED', 'first@nav.no', ?, '{}'::jsonb)
            """.trimIndent(),
            UUID.randomUUID(), firstId,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_audit_events (
                    id, action, outcome, actor_nav_no_email, target_participant_id, details
                ) VALUES (?, 'PARTICIPANT_REJOINED', 'SUCCEEDED', 'first@nav.no', ?, '{}'::jsonb)
            """.trimIndent(),
            UUID.randomUUID(), secondId,
        )
        val creditId = UUID.randomUUID()
        val seasonId = jdbcTemplate.queryForObject(
            "SELECT id FROM program_seasons WHERE ends_on IS NULL",
            UUID::class.java,
        )!!
        jdbcTemplate.update(
            """
                INSERT INTO activity_credits (
                    id, participant_id, season_id, credit_type, uniqueness_key, source_reference, points
                ) VALUES (?, ?, ?, 'DELTA_REGISTRATION', 'event:1', 'Event 1', 1)
            """.trimIndent(),
            creditId,
            firstId,
            seasonId,
        )
        jdbcTemplate.update(
            """
                INSERT INTO point_adjustments (
                    participant_id, season_id, source_credit_id, points_delta, reason,
                    actor_nav_no_email, score_before, score_after
                ) VALUES (?, ?, ?, -1, 'Correction', 'admin@nav.no', 1, 0)
            """.trimIndent(),
            firstId,
            seasonId,
            creditId,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    participant_id, actor_nav_no_email, action, before_values, after_values
                ) VALUES (?, ?, 'POINTS_ADJUSTED', '{}'::jsonb, '{}'::jsonb)
            """.trimIndent(),
            firstId,
            "first@nav.no",
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    participant_id, actor_nav_no_email, action, before_values, after_values
                ) VALUES (?, ?, 'POINTS_ADJUSTED', '{}'::jsonb, '{}'::jsonb)
            """.trimIndent(),
            secondId,
            "first@nav.no",
        )

        val deletion = repository.permanentlyDelete(
            firstId,
        )

        assertThat(deletion).isEqualTo(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM program_audit_events WHERE target_participant_id = ?",
                Int::class.java, firstId,
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForMap(
                "SELECT actor_nav_no_email FROM program_audit_events WHERE target_participant_id = ?",
                secondId,
            )["actor_nav_no_email"]
        ).isNull()
        assertThat(repository.findByNavNoEmail("first@nav.no")).isNull()
        assertThat(repository.findByNavNoEmail("second@nav.no")).isNotNull
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
                first.id,
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_credits WHERE participant_id = ?",
                Int::class.javaObjectType,
                firstId,
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM point_adjustments WHERE participant_id = ?",
                Int::class.javaObjectType,
                firstId,
            )
        ).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM program_scoring_audit WHERE participant_id = ?",
                Int::class.javaObjectType,
                firstId,
            )
        ).isZero()
        val remainingAudit = jdbcTemplate.queryForMap(
            """
                SELECT actor_nav_no_email FROM program_scoring_audit
                WHERE participant_id = ? AND action = 'POINTS_ADJUSTED'
            """.trimIndent(),
            secondId,
        )
        assertThat(remainingAudit["actor_nav_no_email"]).isNull()
    }

    @Test
    fun `should update profile details without changing participant status`() {
        repository.enroll("user@nav.no", "A12345", "user@nav.no")
        repository.updateStatus(
            requireNotNull(repository.findByNavNoEmail("user@nav.no")).id,
            active = false,
            actorNavNoEmail = "admin@nav.no",
        )

        val update = repository.updateProfile(
            navIdent = "A12345",
            email = "user@nav.no",
            fullname = "Updated User",
            teams = listOf("Team A", "Team B"),
        )

        val participant = requireNotNull(repository.findByNavNoEmail("user@nav.no"))
        assertThat(update).isEqualTo(1)
        assertThat(participant.fullname).isEqualTo("Updated User")
        assertThat(participant.teams).containsExactly("Team A", "Team B")
        assertThat(participant.status).isEqualTo(ParticipationStatus.DEACTIVATED)
    }
}
