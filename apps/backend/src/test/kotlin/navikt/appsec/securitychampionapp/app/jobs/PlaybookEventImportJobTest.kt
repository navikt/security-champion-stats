package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.integrations.playbook.PlaybookEvent
import navikt.appsec.securitychampionapp.integrations.playbook.PlaybookEventClient
import navikt.appsec.securitychampionapp.integrations.postgress.PlaybookEventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PlaybookEventImportJobTest {
    private val client = mock<PlaybookEventClient>()
    private val repository = mock<PlaybookEventRepository>()
    private val lock = mock<PostgresJobLock>()
    private val trigger = mock<ScoringSyncTrigger>()

    @Test
    fun `should do nothing when disabled`() {
        val job = PlaybookEventImportJob(client, repository, lock, trigger, false)
        job.importPlaybookEvents()
        assertThat(job.triggerManualImport()).isEqualTo(SyncTriggerResult.DISABLED)
        verifyNoInteractions(client, repository, lock, trigger)
    }

    @Test
    fun `should replace snapshot only after a successful fetch under the job lock`() {
        executeUnderLock()
        val events = listOf(PlaybookEvent(
            "playbook:test", "Test", "2026-10-21", "2026-10-21", "Alle", "https://example.org",
        ))
        whenever(client.fetchEvents()).thenReturn(events)

        PlaybookEventImportJob(client, repository, lock, trigger, true).importPlaybookEvents()

        verify(repository).replaceSnapshot(events)
    }

    @Test
    fun `should retain snapshot when feed is unavailable`() {
        executeUnderLock()
        whenever(client.fetchEvents()).thenThrow(IllegalStateException("unavailable"))

        PlaybookEventImportJob(client, repository, lock, trigger, true).importPlaybookEvents()

        verifyNoInteractions(repository)
    }

    @Test
    fun `should use the same lock for manual imports`() {
        whenever(trigger.trigger(eq(1_006L), eq("importPlaybookEvents"), any()))
            .thenReturn(SyncTriggerResult.STARTED)
        assertThat(PlaybookEventImportJob(client, repository, lock, trigger, true).triggerManualImport())
            .isEqualTo(SyncTriggerResult.STARTED)
    }

    private fun executeUnderLock() {
        doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(lock).runWithLock(eq(1_006L), eq("importPlaybookEvents"), any())
    }
}
