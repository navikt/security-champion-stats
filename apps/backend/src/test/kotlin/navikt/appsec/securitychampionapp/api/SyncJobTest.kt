package navikt.appsec.securitychampionapp.api

import com.slack.api.methods.MethodsClient
import navikt.appsec.securitychampionapp.app.SyncJob
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackService
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalogMock
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlMember
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncJobTest {
    private val jobLock = Mockito.mock(PostgresJobLock::class.java)
    private val environment = StandardEnvironment().apply { setActiveProfiles("test") }
    private val resourceLoader = DefaultResourceLoader()
    private val objectMapper = ObjectMapper()
    private val teamCatalogMock = TeamCatalogMock(objectMapper, resourceLoader)
    private val catalog = TeamCatalog(
        externalServiceWebClient = WebClient.builder().build(),
        teamCatalogMock = teamCatalogMock,
        environment = environment,
    )
    private val slackService = SlackService(
        client = Mockito.mock(MethodsClient::class.java),
        objectMapper = objectMapper,
        resourceLoader = resourceLoader,
        environment = environment,
        playbookUrl = "https://sikkerhet.nav.no/docs/ny-security-champion/",
        scChannelId = "sc-channel-id",
        appSecActivityChannelId = "appsec-channel-id",
    )

    companion object {
        @JvmStatic
        @Container
        val postgres = org.testcontainers.containers.PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var repository: PostgresRepository
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

    private fun syncJob(activityPoints: String = "10") = SyncJob(
        jobLock = jobLock,
        repo = repository,
        catalog = catalog,
        slackService = slackService,
        activityPoints = activityPoints,
        scChannelId = "sc-channel-id",
        appSecId = "appsec-channel-id",
    )

    private fun runJobInsideLock() {
        doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(jobLock).runWithLock(any(), any(), any())
    }

    @Test
    fun `should add new members and remove members no longer in team catalog`() {
        runJobInsideLock()
        seedMember(
            id = "test-id",
            fullname = "Test User",
            email = "test@nav.no",
        )

        syncJob().syncDatabase()

        val members = repository.getAllMembers()
        assertThat(members).hasSize(5)
        assertThat(members.map(SqlMember::email)).containsExactlyInAnyOrder(
            "ada.lovelace@nav.no",
            "local.user@nav.no",
            "thomas.aasen@nav.no",
            "ingrid.moen@nav.no",
            "sara.berg@nav.no"
        )
        assertThat(members.map(SqlMember::email)).doesNotContain("test@nav.no")
    }

    @Test
    fun `should add points for members with slack activity older than two days`() {
        runJobInsideLock()
        seedMember(
            id = "A123456",
            fullname = "Ada Lovelace",
            email = "ada.lovelace@nav.no",
            inProgram = true,
            lastUpdated = Instant.now().minus(3, ChronoUnit.DAYS),
        )

        syncJob().syncDatabase()

        assertThat(repository.getMemberByEmail("ada.lovelace@nav.no")?.points).isEqualTo(20)
    }

    @Test
    fun `should skip point updates for recently updated members and members without slack activity`() {
        runJobInsideLock()
        seedMember(
            id = "A123456",
            fullname = "Ada Lovelace",
            email = "ada.lovelace@nav.no",
            inProgram = true,
            lastUpdated = Instant.now().minus(1, ChronoUnit.DAYS),
        )
        seedMember(
            id = "A1234544426",
            fullname = "Local User",
            email = "local.user@nav.no",
            inProgram = true,
            lastUpdated = Instant.now().minus(3, ChronoUnit.DAYS),
        )

        syncJob().syncDatabase()

        assertThat(repository.getMemberByEmail("ada.lovelace@nav.no")?.points).isEqualTo(0)
        assertThat(repository.getMemberByEmail("local.user@nav.no")?.points).isEqualTo(0)
    }

    @Test
    fun `should skip sync when another instance already holds the lock`() {
        syncJob().syncDatabase()

        verify(jobLock).runWithLock(any(), any(), any())
        assertThat(repository.getAllMembers()).isEmpty()
    }

    private fun seedMember(
        id: String,
        fullname: String,
        email: String,
        inProgram: Boolean = false,
        lastUpdated: Instant? = null,
    ) {
        val member = repository.getMemberByEmail(email)
        if (member == null) {
            repository.addMember(fullname, id, email, emptyList())
        }
        if (inProgram) {
            repository.updateInProgram(email, true)
        }
        lastUpdated?.let {
            jdbcTemplate.update(
                "UPDATE Members SET update_at = ? WHERE email = ?",
                Timestamp.from(it),
                email,
            )
        }
    }
}
