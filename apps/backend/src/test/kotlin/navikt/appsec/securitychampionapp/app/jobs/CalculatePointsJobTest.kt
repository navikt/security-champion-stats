package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.DatabaseQueryResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.DatabaseUpdateResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlMember
import navikt.appsec.securitychampionapp.integrations.slack.ActivityService
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackActivitySummary
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackActivitySummaryResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CalculatePointsJobTest {
    private val jobLock = mock<PostgresJobLock>()
    private val slackActivityService = mock<ActivityService>()
    private val repository = mock<PostgresRepository>()
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-03T12:00:00Z"), ZoneOffset.UTC)

    private val job = CalculatePointsJob(
        jobLock = jobLock,
        slackActivityService = slackActivityService,
        repo = repository,
    )

    @BeforeEach
    fun setup() {
        Mockito.reset(jobLock, slackActivityService, repository)
        whenever(repository.addPoints(any(), any())).thenReturn(DatabaseUpdateResponse(isOk = true))
    }

    @Test
    fun `should add calculated points to members in program`() {
        runJobInsideLock()
        whenever(repository.getAllMembersInProgram()).thenReturn(
            DatabaseQueryResponse(
                isOk = true,
                queryResult = listOf(
                    member(id = "member-1", email = "ada.lovelace@nav.no"),
                    member(id = "member-2", email = "local.user@nav.no"),
                )
            )
        )
        whenever(slackActivityService.calculateActivityForMembers(any())).thenReturn(
            SlackActivitySummaryResponse(
                isOk = true,
                slackActivitySummaries = listOf(
                    SlackActivitySummary(email = "ada.lovelace@nav.no", totalPoints = 20),
                    SlackActivitySummary(email = "local.user@nav.no", totalPoints = 10),
                )
            )
        )

        job.calculatePoints()

        val emailCaptor = argumentCaptor<List<String>>()
        verify(slackActivityService).calculateActivityForMembers(emailCaptor.capture())
        assertThat(emailCaptor.firstValue).containsExactly("ada.lovelace@nav.no", "local.user@nav.no")
        verify(repository).addPoints("member-1", 20)
        verify(repository).addPoints("member-2", 10)
    }

    @Test
    fun `should skip calculation when database query fails`() {
        runJobInsideLock()
        whenever(repository.getAllMembersInProgram()).thenReturn(
            DatabaseQueryResponse(
                isOk = false,
                queryResult = emptyList(),
                error = "database unavailable"
            )
        )

        job.calculatePoints()

        verify(slackActivityService, never()).calculateActivityForMembers(any())
        verify(repository, never()).addPoints(any(), any())
    }

    @Test
    fun `should skip point updates when slack activity calculation fails`() {
        runJobInsideLock()
        whenever(repository.getAllMembersInProgram()).thenReturn(
            DatabaseQueryResponse(
                isOk = true,
                queryResult = listOf(member(id = "member-1", email = "ada.lovelace@nav.no"))
            )
        )
        whenever(slackActivityService.calculateActivityForMembers(any())).thenReturn(
            SlackActivitySummaryResponse(
                isOk = false,
                slackActivitySummaries = emptyList(),
                error = "slack unavailable"
            )
        )

        job.calculatePoints()

        verify(repository, never()).addPoints(any(), any())
    }

    @Test
    fun `should continue updating other members when one point update fails`() {
        runJobInsideLock()
        whenever(repository.getAllMembersInProgram()).thenReturn(
            DatabaseQueryResponse(
                isOk = true,
                queryResult = listOf(
                    member(id = "member-1", email = "ada.lovelace@nav.no"),
                    member(id = "member-2", email = "local.user@nav.no"),
                )
            )
        )
        whenever(slackActivityService.calculateActivityForMembers(any())).thenReturn(
            SlackActivitySummaryResponse(
                isOk = true,
                slackActivitySummaries = listOf(
                    SlackActivitySummary(email = "ada.lovelace@nav.no", totalPoints = 20),
                    SlackActivitySummary(email = "local.user@nav.no", totalPoints = 10),
                )
            )
        )
        whenever(repository.addPoints("member-1", 20)).thenReturn(
            DatabaseUpdateResponse(isOk = false, error = "update failed")
        )
        whenever(repository.addPoints("member-2", 10)).thenReturn(DatabaseUpdateResponse(isOk = true))

        job.calculatePoints()

        verify(repository).addPoints("member-1", 20)
        verify(repository).addPoints("member-2", 10)
    }

    @Test
    fun `should skip calculation when another instance already holds the lock`() {
        job.calculatePoints()

        verify(jobLock).runWithLock(any(), any(), any())
        verify(repository, never()).getAllMembersInProgram()
        verify(slackActivityService, never()).calculateActivityForMembers(any())
    }

    private fun runJobInsideLock() {
        doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(jobLock).runWithLock(any(), any(), any())
    }

    private fun member(
        id: String,
        email: String,
        points: Int = 0,
        inProgram: Boolean = true,
    ) = SqlMember(
        id = id,
        fullname = "Test User",
        points = points,
        lastUpdated = "2026-08-01T12:00:00Z",
        email = email,
        inProgram = inProgram,
        level = "1",
        teams = emptyList(),
    )
}
