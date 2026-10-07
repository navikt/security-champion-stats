package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ProgramAuditPurgeJobTest {
    private val jobLock = mock<PostgresJobLock>()
    private val auditService = mock<ProgramAuditService>()

    @Test
    fun `should purge expired events under the shared database job lock`() {
        doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(jobLock).runWithLock(eq(1_007L), eq("purgeExpiredOperationalEvents"), any())

        ProgramAuditPurgeJob(jobLock, auditService).purgeExpiredOperationalEvents()

        verify(auditService).purgeExpiredOperationalEvents()
    }

    @Test
    fun `should not purge expired events when another pod holds the lock`() {
        ProgramAuditPurgeJob(jobLock, auditService).purgeExpiredOperationalEvents()

        verify(jobLock).runWithLock(eq(1_007L), eq("purgeExpiredOperationalEvents"), any())
        verifyNoInteractions(auditService)
    }
}
