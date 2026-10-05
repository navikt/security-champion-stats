package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.scoring.SlackScoringService
import navikt.appsec.securitychampionapp.app.scoring.SlackSyncSummary
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.SlackScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackIntegrationException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant

class SlackScoringSyncJobTest {
    private val attemptAt = Instant.parse("2026-10-05T12:00:00Z")
    private val clock = Clock.fixed(attemptAt, Clock.systemUTC().zone)

    @Test
    fun `should persist successful Slack sync summary`() {
        val jobLock = mock<PostgresJobLock>()
        val scoringService = mock<SlackScoringService>()
        val statusRepository = mock<SlackScoringStatusRepository>()
        runLocked(jobLock)
        whenever(scoringService.sync("C123", attemptAt)).thenReturn(
            SlackSyncSummary(messagesScanned = 12, creditsAwarded = 2, duplicateCredits = 1, unmappedAuthors = 3),
        )
        val job = SlackScoringSyncJob(jobLock, scoringService, statusRepository, "C123", clock)

        job.syncSlackScoring()

        verify(statusRepository).recordStarted(attemptAt)
        verify(statusRepository).recordSucceeded(
            attemptAt,
            SlackSyncSummary(messagesScanned = 12, creditsAwarded = 2, duplicateCredits = 1, unmappedAuthors = 3),
        )
    }

    @Test
    fun `should store only a sanitized failure summary when Slack fails`() {
        val jobLock = mock<PostgresJobLock>()
        val scoringService = mock<SlackScoringService>()
        val statusRepository = mock<SlackScoringStatusRepository>()
        runLocked(jobLock)
        whenever(scoringService.sync("C123", attemptAt))
            .thenThrow(SlackIntegrationException("raw provider error with sensitive payload"))
        val job = SlackScoringSyncJob(jobLock, scoringService, statusRepository, "C123", clock)

        job.syncSlackScoring()

        verify(statusRepository).recordFailed(
            attemptAt,
            "Slack activity could not be synchronized; check API access and channel configuration",
        )
    }

    private fun runLocked(jobLock: PostgresJobLock) {
        doAnswer {
            it.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(jobLock).runWithLock(any(), any(), any())
    }
}
