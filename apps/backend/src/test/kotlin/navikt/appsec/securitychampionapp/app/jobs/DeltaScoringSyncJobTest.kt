package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Assertions.assertEquals

class DeltaScoringSyncJobTest {
    @Test
    fun `should not acquire a lock or call Delta when scoring is disabled`() {
        val jobLock = mock<PostgresJobLock>()
        val syncTrigger = mock<ScoringSyncTrigger>()
        val scoringService = mock<DeltaScoringService>()
        val job = DeltaScoringSyncJob(jobLock, syncTrigger, scoringService, enabled = false)

        job.syncDeltaScoring()

        verifyNoInteractions(jobLock, scoringService)
    }

    @Test
    fun `should reject manual triggers when Delta scoring is disabled`() {
        val syncTrigger = mock<ScoringSyncTrigger>()
        val job = DeltaScoringSyncJob(mock(), syncTrigger, mock(), enabled = false)

        assertEquals(SyncTriggerResult.DISABLED, job.triggerManualSync())

        verifyNoInteractions(syncTrigger)
    }

    @Test
    fun `should delegate manual sync triggers when Delta scoring is enabled`() {
        val syncTrigger = mock<ScoringSyncTrigger>()
        whenever(syncTrigger.trigger(org.mockito.kotlin.eq(1_003L), org.mockito.kotlin.eq("syncDeltaScoring"), org.mockito.kotlin.any()))
            .thenReturn(SyncTriggerResult.STARTED)
        val job = DeltaScoringSyncJob(mock(), syncTrigger, mock(), enabled = true)

        assertEquals(SyncTriggerResult.STARTED, job.triggerManualSync())

        verify(syncTrigger).trigger(org.mockito.kotlin.eq(1_003L), org.mockito.kotlin.eq("syncDeltaScoring"), org.mockito.kotlin.any())
    }
}
