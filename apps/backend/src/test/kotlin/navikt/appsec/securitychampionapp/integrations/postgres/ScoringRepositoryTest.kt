package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.CreditAwardResult
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.integrations.postgress.ScoringRepository
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScoringRepositoryTest {
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
    private lateinit var repository: ScoringRepository
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
        repository = ScoringRepository(jdbcTemplate)
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
    fun `should award fixed points once per participant activity key`() {
        val participantId = createParticipant("person@nav.no")

        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.GITHUB_PULL_REQUEST,
                "pr:42",
                "Pull request 42",
            )
        ).isEqualTo(CreditAwardResult.AWARDED)
        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.GITHUB_PULL_REQUEST,
                "pr:42",
                "Pull request 42",
            )
        ).isEqualTo(CreditAwardResult.DUPLICATE)

        val overview = ScoringService(repository).adminOverview()
        assertThat(overview.participants.single().points).isEqualTo(3)
        assertThat(repository.creditsForParticipant(participantId).single().points).isEqualTo(3)
    }

    @Test
    fun `should assign an adjustment to the source credit season`() {
        val participantId = createParticipant("person@nav.no")
        val today = LocalDate.now(ZoneId.of("Europe/Oslo"))
        val firstSeason = repository.currentSeason()
        repository.awardCredit(participantId, ActivityCreditType.DELTA_REGISTRATION, "event:1", "Event 1")

        repository.resetManually(today.plusDays(1), "Start next season", "admin@nav.no")
        repository.resetManually(today.plusDays(2), "Start another season", "admin@nav.no")

        val creditId = repository.creditsForParticipant(participantId).single().id
        val adjustment = repository.addAdjustment(
            participantId,
            -1,
            "Correct duplicate registration",
            "admin@nav.no",
            creditId,
        )

        assertThat(adjustment.seasonId).isEqualTo(firstSeason.id)
        assertThat(adjustment.scoreBefore).isEqualTo(1)
        assertThat(adjustment.scoreAfter).isZero()
        assertThat(repository.scoreForParticipant(participantId, firstSeason.id)).isZero()
        assertThat(repository.scoreForParticipant(participantId, repository.currentSeason().id)).isZero()
    }

    @Test
    fun `should preserve season history and advance the scheduled reset`() {
        val participantId = createParticipant("person@nav.no")
        val initialSeason = repository.currentSeason()
        repository.awardCredit(participantId, ActivityCreditType.SLACK_WEEK, "2026-W40", "2026-W40")

        assertThat(repository.resetIfDue(initialSeason.nextResetDate)).isTrue()

        val nextSeason = repository.currentSeason()
        assertThat(nextSeason.startsOn).isEqualTo(initialSeason.nextResetDate)
        assertThat(nextSeason.nextResetDate).isEqualTo(LocalDate.of(initialSeason.nextResetDate.year + 1, 1, 1))
        assertThat(repository.scoreForParticipant(participantId, initialSeason.id)).isEqualTo(1)
        assertThat(repository.scoreForParticipant(participantId, nextSeason.id)).isZero()
    }

    @Test
    fun `should rank ties together and omit zero score participants`() {
        val service = ScoringService(repository)
        val participantIds = (1..6).map { createParticipant("person$it@nav.no") }
        val zeroScoreParticipant = createParticipant("zero@nav.no")
        participantIds.forEachIndexed { index, id ->
            repository.awardCredit(id, ActivityCreditType.SLACK_WEEK, "week:$index", "week $index")
        }

        val recognition = service.recognition()
        val leaderboard = service.leaderboard()

        assertThat(recognition).hasSize(6).allSatisfy { assertThat(it.rank).isEqualTo(1) }
        assertThat(leaderboard).hasSize(6).allSatisfy {
            assertThat(it.rank).isEqualTo(1)
            assertThat(it.points).isEqualTo(1)
        }
        assertThat(zeroScoreParticipant).isNotNull()
        assertThat(recognition.map { it.fullName }).doesNotContain("zero")
    }

    @Test
    fun `should reject awards for inactive participants without creating credits`() {
        val participantId = createParticipant("person@nav.no")
        jdbcTemplate.update(
            "UPDATE program_participants SET status = 'DEACTIVATED' WHERE id = ?",
            participantId,
        )

        val result = repository.awardCredit(
            participantId,
            ActivityCreditType.SLACK_WEEK,
            "week:1",
            "week 1",
        )

        assertThat(result).isEqualTo(CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING)
        assertThat(repository.creditsForParticipant(participantId)).isEmpty()
    }

    private fun createParticipant(email: String): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
                INSERT INTO program_participants (id, nav_no_email, nav_ident, email, fullname)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            email,
            email.substringBefore('@'),
            email,
            email.substringBefore('@'),
        )
        return id
    }
}
