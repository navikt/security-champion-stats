package navikt.appsec.securitychampionapp.app.jobs

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlMember
import navikt.appsec.securitychampionapp.integrations.slack.ChannelMembershipService
import navikt.appsec.securitychampionapp.integrations.slack.dto.SecurityChampion
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalogMock
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.MemberWithTeamData
import org.assertj.core.api.Assertions
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.reactive.function.client.WebClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncJobTest {
    private val jobLock = Mockito.mock(PostgresJobLock::class.java)
    private val slackChannelMembershipService = mock<ChannelMembershipService>()
    private val environment = StandardEnvironment().apply { setActiveProfiles("test") }
    private val resourceLoader = DefaultResourceLoader()
    private val objectMapper = ObjectMapper()
    private val teamCatalogMock = TeamCatalogMock(objectMapper, resourceLoader)
    private val catalog = TeamCatalog(
        externalServiceWebClient = WebClient.builder().build(),
        teamCatalogMock = teamCatalogMock,
        environment = environment,
    )

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
        Mockito.reset(jobLock, slackChannelMembershipService)
        flyway.clean()
        flyway.migrate()
        whenever(slackChannelMembershipService.sendWelcomeMessage(any())).thenReturn(SlackResponse(isOk = true))
        whenever(slackChannelMembershipService.updateUserGroupWithNewMembers()).thenReturn(SlackResponse(isOk = true))
    }

    private fun syncJob(catalogOverride: TeamCatalog = catalog) = SyncJob(
        jobLock = jobLock,
        repo = repository,
        catalog = catalogOverride,
        slackChannelMembershipService = slackChannelMembershipService,
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

        val members = repository.getAllMembers().queryResult!!
        Assertions.assertThat(members).hasSize(5)
        Assertions.assertThat(members.map(SqlMember::email)).containsExactlyInAnyOrder(
            "ada.lovelace@nav.no",
            "local.user@nav.no",
            "thomas.aasen@nav.no",
            "ingrid.moen@nav.no",
            "sara.berg@nav.no"
        )
        Assertions.assertThat(members.map(SqlMember::email)).doesNotContain("test@nav.no")
        verify(slackChannelMembershipService).updateUserGroupWithNewMembers()
    }

    @Test
    fun `should send welcome message for new members`() {
        runJobInsideLock()

        syncJob().syncDatabase()

        val captor = argumentCaptor<List<SecurityChampion>>()
        verify(slackChannelMembershipService).sendWelcomeMessage(captor.capture())
        Assertions.assertThat(captor.firstValue.map(SecurityChampion::email)).containsExactlyInAnyOrder(
            "ada.lovelace@nav.no",
            "local.user@nav.no",
            "thomas.aasen@nav.no",
            "ingrid.moen@nav.no",
            "sara.berg@nav.no"
        )
    }

    @Test
    fun `should skip sync when team catalog returns no members`() {
        runJobInsideLock()
        seedMember(
            id = "test-id",
            fullname = "Test User",
            email = "test@nav.no",
        )
        val emptyCatalog = mock<TeamCatalog>()
        whenever(emptyCatalog.fetchMembersWithRole()).thenReturn(emptyList())

        syncJob(emptyCatalog).syncDatabase()

        Assertions.assertThat(repository.getMemberByEmail("test@nav.no").queryResult).hasSize(1)
        verify(slackChannelMembershipService, never()).sendWelcomeMessage(any())
        verify(slackChannelMembershipService, never()).updateUserGroupWithNewMembers()
    }

    @Test
    fun `should not update user group when welcome message fails`() {
        runJobInsideLock()
        whenever(slackChannelMembershipService.sendWelcomeMessage(any()))
            .thenReturn(SlackResponse(isOk = false, error = "slack failed"))

        syncJob().syncDatabase()

        verify(slackChannelMembershipService).sendWelcomeMessage(any())
        verify(slackChannelMembershipService, never()).updateUserGroupWithNewMembers()
    }

    @Test
    fun `should update teams for existing members`() {
        runJobInsideLock()
        seedMember(
            id = "A123456",
            fullname = "Ada Lovelace",
            email = "ada.lovelace@nav.no",
            teams = listOf("Old team"),
        )
        val catalogWithUpdatedTeam = mock<TeamCatalog>()
        whenever(catalogWithUpdatedTeam.fetchMembersWithRole()).thenReturn(
            listOf(
                MemberWithTeamData(
                    navIdent = "A123456",
                    fullName = "Ada Lovelace",
                    email = "ada.lovelace@nav.no",
                    teamName = mutableListOf("New team"),
                    teamId = mutableListOf("new-team-id"),
                )
            )
        )

        syncJob(catalogWithUpdatedTeam).syncDatabase()

        Assertions.assertThat(repository.getMemberByEmail("ada.lovelace@nav.no").queryResult!!.first().teams)
            .containsExactly("New team")
    }

    @Test
    fun `should skip sync when another instance already holds the lock`() {
        syncJob().syncDatabase()

        verify(jobLock).runWithLock(any(), any(), any())
        Assertions.assertThat(repository.getAllMembers().queryResult).isEmpty()
    }

    private fun seedMember(
        id: String,
        fullname: String,
        email: String,
        teams: List<String> = emptyList(),
    ) {
        val member = repository.getMemberByEmail(email).queryResult!!.firstOrNull()
        if (member == null) {
            repository.addMember(fullname, id, email, teams)
        }
    }
}