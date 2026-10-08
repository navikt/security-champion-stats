package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.*
import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.participation.ParticipantEnrolledEvent
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import java.util.UUID

class SlackIdentityProvisioningJobTest {
    private val service = mock<SlackIdentityProvisioningService>()
    private val lock = mock<PostgresJobLock>()
    private val lease = mock<PostgresJobLock.LockLease>()
    private val executor = mock<TaskExecutor>()
    private val audit = mock<ProgramAuditService>()
    private val job = SlackIdentityProvisioningJob(service, lock, executor, audit)
    private val id = UUID.randomUUID()

    init {
        whenever(lock.tryAcquireLock(1_010L, "provisionSlackIdentities")).thenReturn(lease)
        whenever(service.provision(anyOrNull())).thenReturn(SlackIdentityProvisioningSummary(1, 0, 0))
    }

    @Test
    fun `enrollment only queues the lookup and does not synchronously call Slack or the database`() {
        job.enrolled(ParticipantEnrolledEvent(id))
        verifyNoInteractions(service, lock, audit)
        val task = argumentCaptor<Runnable>()
        verify(executor).execute(task.capture())
        task.firstValue.run()
        verify(service).provision(id)
        verify(audit).record(eq("SLACK_IDENTITY_LOOKUP_COMPLETED"), eq(AuditOutcome.SUCCEEDED),
            isNull(), eq(id), isNull(), any())
        verify(lease).close()
    }

    @Test
    fun `a busy executor defers lookup without failing enrollment`() {
        doThrow(TaskRejectedException("Busy")).whenever(executor).execute(any())
        job.enrolled(ParticipantEnrolledEvent(id))
        verifyNoInteractions(service, lock)
        verify(audit).record(eq("SLACK_IDENTITY_LOOKUP_DEFERRED"), eq(AuditOutcome.PARTIAL),
            isNull(), eq(id), isNull(), any())
    }

    @Test
    fun `scheduled backfill uses the same cross instance lock as enrollment lookups`() {
        whenever(service.provision(null)).thenReturn(SlackIdentityProvisioningSummary(0, 1, 0))
        job.reconcile()
        verify(lock).tryAcquireLock(1_010L, "provisionSlackIdentities")
        verify(service).provision(null)
        verify(audit).record(eq("SLACK_IDENTITY_LOOKUP_COMPLETED"), eq(AuditOutcome.PARTIAL),
            isNull(), isNull(), isNull(), any())
        verify(lease).close()
        verifyNoInteractions(executor)
    }

    @Test
    fun `another active lookup defers reconciliation without touching Slack`() {
        whenever(lock.tryAcquireLock(any(), any())).thenReturn(null)
        job.reconcile()
        verifyNoInteractions(service, audit)
    }

    @Test
    fun `background failures are recorded and release the lock without rolling back enrollment`() {
        whenever(service.provision(id)).thenThrow(IllegalStateException("Unavailable"))
        job.enrolled(ParticipantEnrolledEvent(id))
        val task = argumentCaptor<Runnable>()
        verify(executor).execute(task.capture())
        assertThatThrownBy { task.firstValue.run() }.isInstanceOf(IllegalStateException::class.java)
        verify(audit).record(eq("SLACK_IDENTITY_LOOKUP_FAILED"), eq(AuditOutcome.FAILED),
            isNull(), eq(id), isNull(), any())
        verify(lease).close()
    }
}
