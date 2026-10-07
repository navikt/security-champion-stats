package navikt.appsec.securitychampionapp.app.jobs

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.CreditAwardResult
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresScoringLedger
import navikt.appsec.securitychampionapp.integrations.postgress.SlackIdentityMappingRepository
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.MemberWithTeamData
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncJobTest {
    private val jobLock = Mockito.mock(PostgresJobLock::class.java)
    private val catalog = mock<TeamCatalog>()

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
    fun setup() {
        Mockito.reset(jobLock, catalog)
        flyway.clean()
        flyway.migrate()
    }

    private fun syncJob() = SyncJob(
        jobLock = jobLock,
        repo = repository,
        catalog = catalog,
    )

    private fun runJobInsideLock() {
        doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(jobLock).runWithLock(any(), any(), any())
    }

    @Test
    fun `should update profile data without changing participation`() {
        runJobInsideLock()
        repository.enroll("user@nav.no", "A12345", "user@nav.no")
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(
            listOf(
                MemberWithTeamData(
                    navIdent = "A12345",
                    fullName = "Test User",
                    email = "user@nav.no",
                    teamName = mutableListOf("Updated team"),
                    teamId = mutableListOf("team-id"),
                )
            )
        )

        syncJob().syncDatabase()

        val participant = requireNotNull(repository.findByNavNoEmail("user@nav.no"))
        assertThat(participant.fullname).isEqualTo("Test User")
        assertThat(participant.teams).containsExactly("Updated team")
        assertThat(participant.status).isEqualTo(ParticipationStatus.ACTIVE)
    }

    @Test
    fun `should not create participants for Teamkatalogen members`() {
        runJobInsideLock()
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(
            listOf(
                MemberWithTeamData(
                    navIdent = "A12345",
                    fullName = "Not Enrolled",
                    email = "not-enrolled@nav.no",
                    teamName = mutableListOf("Team"),
                    teamId = mutableListOf("team-id"),
                )
            )
        )

        syncJob().syncDatabase()

        assertThat(repository.findAllParticipants()).isEmpty()
    }

    @Test
    fun `should delete Slack mappings and credits without restoring a participant during sync`() {
        runJobInsideLock()
        assertThat(repository.enroll("deleted@nav.no", "A12345", "deleted@nav.no")).isEqualTo(1)
        val participantId = requireNotNull(repository.findByNavNoEmail("deleted@nav.no")).id
        val mappings = SlackIdentityMappingRepository(jdbcTemplate)
        val scoring = PostgresScoringLedger(jdbcTemplate)
        assertThat(mappings.addMapping("U_DELETED", participantId, "admin@nav.no")).isTrue()
        assertThat(
            scoring.awardCredit(participantId, ActivityCreditType.SLACK_WEEK, "2026-10-05", "U_DELETED:message")
        ).isEqualTo(CreditAwardResult.AWARDED)
        assertThat(mappings.mappedParticipants()).containsKey("U_DELETED")
        assertThat(scoring.creditsForParticipant(participantId)).hasSize(1)
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(
            listOf(
                MemberWithTeamData(
                    navIdent = "A12345",
                    fullName = "Deleted Participant",
                    email = "deleted@nav.no",
                    teamName = mutableListOf("Updated team"),
                    teamId = mutableListOf("team-id"),
                )
            )
        )

        assertThat(repository.permanentlyDelete(participantId)).isEqualTo(1)
        assertThat(mappings.mappingOverview().first).isEmpty()
        assertThat(scoring.creditsForParticipant(participantId)).isEmpty()

        syncJob().syncDatabase()

        verify(catalog).fetchAllMembersWithTeamData()
        assertThat(repository.findAllParticipants()).isEmpty()
        assertThat(scoring.participantExists(participantId)).isFalse()
        assertThat(mappings.mappingOverview().first).isEmpty()
        assertThat(mappings.mappedParticipants()).isEmpty()
        assertThat(scoring.creditsForParticipant(participantId)).isEmpty()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM program_participant_audit WHERE participant_id = ?",
                Int::class.javaObjectType,
                participantId,
            )
        ).isZero()
    }

    @Test
    fun `should retain participants absent from Teamkatalogen`() {
        runJobInsideLock()
        repository.enroll("user@nav.no", "A12345", "user@nav.no")
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(emptyList())

        syncJob().syncDatabase()

        assertThat(repository.findByNavNoEmail("user@nav.no")).isNotNull
    }

    @Test
    fun `should not sync when another instance holds the lock`() {
        syncJob().syncDatabase()

        verify(jobLock).runWithLock(any(), any(), any())
        verify(catalog, Mockito.never()).fetchAllMembersWithTeamData()
    }

}
