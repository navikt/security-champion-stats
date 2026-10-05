package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class DeltaScoringSyncJobTest {
    @Test
    fun `should not acquire a lock or call Delta when scoring is disabled`() {
        val jobLock = mock<PostgresJobLock>()
        val scoringService = mock<DeltaScoringService>()
        val job = DeltaScoringSyncJob(jobLock, scoringService, enabled = false)

        job.syncDeltaScoring()

        verifyNoInteractions(jobLock, scoringService)
    }
}
