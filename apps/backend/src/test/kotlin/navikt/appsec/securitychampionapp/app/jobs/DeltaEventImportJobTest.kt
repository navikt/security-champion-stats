package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.events.DeltaEventImportService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DeltaEventImportJobTest {
    @Test
    fun `should not acquire a lock or call Delta when import is disabled`() {
        val jobLock = mock<PostgresJobLock>()
        val importService = mock<DeltaEventImportService>()
        val syncTrigger = mock<ScoringSyncTrigger>()
        val job = DeltaEventImportJob(jobLock, syncTrigger, importService, enabled = false)

        job.importDeltaEvents()

        assertEquals(SyncTriggerResult.DISABLED, job.triggerManualImport())
        verifyNoInteractions(jobLock, syncTrigger, importService)
    }

    @Test
    fun `should delegate manual import triggers when import is enabled`() {
        val syncTrigger = mock<ScoringSyncTrigger>()
        whenever(syncTrigger.trigger(eq(1_005L), eq("importDeltaEvents"), any()))
            .thenReturn(SyncTriggerResult.STARTED)
        val job = DeltaEventImportJob(mock(), syncTrigger, mock(), enabled = true)

        assertEquals(SyncTriggerResult.STARTED, job.triggerManualImport())
    }
}
