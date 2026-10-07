package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.GitHubScoringService
import navikt.appsec.securitychampionapp.app.scoring.GitHubSyncSummary
import navikt.appsec.securitychampionapp.integrations.github.GitHubFailure
import navikt.appsec.securitychampionapp.integrations.github.GitHubIntegrationException
import navikt.appsec.securitychampionapp.integrations.postgress.GitHubScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GitHubScoringSyncJobTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private val lock = mock<PostgresJobLock>()
    private val trigger = mock<ScoringSyncTrigger>()
    private val service = mock<GitHubScoringService>()
    private val status = mock<GitHubScoringStatusRepository>()
    private val audit = mock<ProgramAuditService>()

    @Test
    fun `should persist successful results under the scheduled lock`() {
        runLocked()
        val summary = GitHubSyncSummary(5, 2, 1, 1)
        whenever(service.sync(eq(now), anyOrNull())).thenReturn(summary)
        job().syncGitHubScoring()
        verify(lock).runWithLock(eq(ScoringJobLockKeys.GITHUB), eq("syncGitHubScoring"), any())
        verify(status).recordStarted(now)
        verify(status).recordSucceeded(now, summary)
    }

    @Test
    fun `should persist sanitized SAML access failures`() {
        runLocked()
        whenever(service.sync(eq(now), anyOrNull()))
            .thenThrow(GitHubIntegrationException(GitHubFailure.IDENTITY, "samlProviderNull"))
        job().syncGitHubScoring()
        verify(status).recordFailed(GitHubFailure.IDENTITY.summary)
    }

    @Test
    fun `should persist rate limit guidance instead of permission errors`() {
        runLocked()
        whenever(service.sync(eq(now), anyOrNull()))
            .thenThrow(GitHubIntegrationException(GitHubFailure.RATE_LIMIT))
        job().syncGitHubScoring()
        verify(status).recordFailed(GitHubFailure.RATE_LIMIT.summary)
    }

    @Test
    fun `should block disabled syncs without acquiring a token or lock`() {
        val job = job(enabled = false)
        job.syncGitHubScoring()
        assertThat(job.triggerManualSync("admin@nav.no")).isEqualTo(SyncTriggerResult.DISABLED)
        verifyNoInteractions(lock, trigger, service, status, audit)
    }

    @Test
    fun `should use the same lock and preserve the manual actor for background sync`() {
        whenever(
            trigger.trigger(eq(ScoringJobLockKeys.GITHUB), eq("syncGitHubScoring"), eq("admin@nav.no"), any()),
        ).thenReturn(SyncTriggerResult.STARTED)
        assertThat(job().triggerManualSync("admin@nav.no")).isEqualTo(SyncTriggerResult.STARTED)
        verify(trigger).trigger(eq(ScoringJobLockKeys.GITHUB), eq("syncGitHubScoring"), eq("admin@nav.no"), any())
    }

    private fun job(enabled: Boolean = true) =
        GitHubScoringSyncJob(lock, trigger, service, status, audit, Clock.fixed(now, ZoneOffset.UTC), enabled)

    private fun runLocked() {
        doAnswer {
            it.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(lock).runWithLock(any(), any(), any())
    }
}
