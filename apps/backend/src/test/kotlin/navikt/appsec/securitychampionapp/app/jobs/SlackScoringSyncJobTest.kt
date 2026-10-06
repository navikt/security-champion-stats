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
        val syncTrigger = mock<ScoringSyncTrigger>()
        val scoringService = mock<SlackScoringService>()
        val statusRepository = mock<SlackScoringStatusRepository>()
        runLocked(jobLock)
        whenever(scoringService.sync("C123", attemptAt)).thenReturn(
            SlackSyncSummary(messagesScanned = 12, creditsAwarded = 2, duplicateCredits = 1, unmappedAuthors = 3),
        )
        val job = SlackScoringSyncJob(jobLock, syncTrigger, scoringService, statusRepository, "C123", clock)

        job.syncSlackScoring()

        verify(statusRepository).recordStarted(attemptAt)
        verify(statusRepository).recordSucceeded(
            attemptAt,
            SlackSyncSummary(messagesScanned = 12, creditsAwarded = 2, duplicateCredits = 1, unmappedAuthors = 3),
        )
    }

    @Test
    fun `should preserve an unfamiliar Slack error code in the failure summary`() {
        val jobLock = mock<PostgresJobLock>()
        val syncTrigger = mock<ScoringSyncTrigger>()
        val scoringService = mock<SlackScoringService>()
        val statusRepository = mock<SlackScoringStatusRepository>()
        runLocked(jobLock)
        whenever(scoringService.sync("C123", attemptAt))
            .thenThrow(
                SlackIntegrationException(
                    SlackIntegrationException.Operation.HISTORY,
                    "request_timeout",
                ),
            )
        val job = SlackScoringSyncJob(jobLock, syncTrigger, scoringService, statusRepository, "C123", clock)

        job.syncSlackScoring()

        verify(statusRepository).recordFailed(
            attemptAt,
            "Slack conversations.history failed: request_timeout; check Slack availability and API access",
        )
    }

    @Test
    fun `should persist the Slack operation and actionable error code when access fails`() {
        val jobLock = mock<PostgresJobLock>()
        val syncTrigger = mock<ScoringSyncTrigger>()
        val scoringService = mock<SlackScoringService>()
        val statusRepository = mock<SlackScoringStatusRepository>()
        runLocked(jobLock)
        whenever(scoringService.sync("C123", attemptAt)).thenThrow(
            SlackIntegrationException(SlackIntegrationException.Operation.REPLIES, "missing_scope"),
        )
        val job = SlackScoringSyncJob(jobLock, syncTrigger, scoringService, statusRepository, "C123", clock)

        job.syncSlackScoring()

        verify(statusRepository).recordFailed(
            attemptAt,
            "Slack conversations.replies failed: missing_scope; check token scopes for this method and channel type",
        )
    }

    @Test
    fun `should delegate manual sync triggers to the guarded trigger service`() {
        val jobLock = mock<PostgresJobLock>()
        val syncTrigger = mock<ScoringSyncTrigger>()
        val job = SlackScoringSyncJob(
            jobLock,
            syncTrigger,
            mock(),
            mock(),
            "C123",
            clock,
        )
        whenever(syncTrigger.trigger(eq(1_002L), eq("syncSlackScoring"), any()))
            .thenReturn(SyncTriggerResult.STARTED)

        val result = job.triggerManualSync()

        org.junit.jupiter.api.Assertions.assertEquals(SyncTriggerResult.STARTED, result)
        verify(syncTrigger).trigger(eq(1_002L), eq("syncSlackScoring"), any())
    }

    private fun runLocked(jobLock: PostgresJobLock) {
        doAnswer {
            it.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(jobLock).runWithLock(any(), any(), any())
    }
}
