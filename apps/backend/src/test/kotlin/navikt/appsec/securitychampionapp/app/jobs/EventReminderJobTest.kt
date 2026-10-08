package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.events.EventReminderConflictException
import navikt.appsec.securitychampionapp.app.events.EventReminderResult
import navikt.appsec.securitychampionapp.app.events.EventReminderService
import navikt.appsec.securitychampionapp.app.events.InvalidEventReminderMessageException
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.core.task.TaskExecutor

class EventReminderJobTest {
    private val lock = mock<PostgresJobLock>()
    private val lease = mock<PostgresJobLock.LockLease>()
    private val executor = mock<TaskExecutor>()
    private val service = mock<EventReminderService>()
    private val audit = mock<ProgramAuditService>()
    private val job = EventReminderJob(service, ScoringSyncTrigger(lock, executor), audit)

    init {
        whenever(lock.tryAcquireLock(1_009L, "sendEventReminders")).thenReturn(lease)
    }

    @Test
    fun `manual reminders validate under the shared lock before queueing and release the lock after delivery`() {
        whenever(service.send("event", "reviewed", "Edited reminder")).thenReturn(EventReminderResult(2, 0, 0, 0))
        assertThat(job.send("event", "reviewed", "Edited reminder", "admin@nav.no")).isEqualTo(SyncTriggerResult.STARTED)
        val task = argumentCaptor<Runnable>()
        inOrder(lock, service, executor) {
            verify(lock).tryAcquireLock(1_009L, "sendEventReminders")
            verify(service).validate("event", "reviewed", "Edited reminder")
            verify(executor).execute(task.capture())
        }
        verify(service, never()).send(any(), any(), any())
        task.firstValue.run()
        verify(service).send("event", "reviewed", "Edited reminder")
        verify(audit).recordRun(eq("EVENT_REMINDERS_COMPLETED"), eq(AuditOutcome.SUCCEEDED), any(), any())
        verify(lease).close()
    }

    @Test
    fun `stale previews never enter the queue and release the lock`() {
        whenever(service.validate(any(), any(), any())).thenThrow(EventReminderConflictException("Changed"))
        assertThatThrownBy { job.send("event", "reviewed", "Edited reminder", "admin@nav.no") }.isInstanceOf(EventReminderConflictException::class.java)
        verifyNoInteractions(executor, audit)
        verify(lease).close()
    }

    @Test
    fun `invalid edited messages never enter the queue and release the lock`() {
        whenever(service.validate(any(), any(), any())).thenThrow(InvalidEventReminderMessageException())
        assertThatThrownBy { job.send("event", "reviewed", " ", "admin@nav.no") }
            .isInstanceOf(InvalidEventReminderMessageException::class.java)
        verifyNoInteractions(executor, audit)
        verify(lease).close()
    }

    @Test
    fun `partial deliveries are recorded rather than reported as full success`() {
        whenever(service.send(any(), any(), any())).thenReturn(EventReminderResult(1, 1, 0, 1))
        job.send("event", "reviewed", "Edited reminder", "admin@nav.no")
        val task = argumentCaptor<Runnable>()
        verify(executor).execute(task.capture())
        task.firstValue.run()
        verify(audit).recordRun(eq("EVENT_REMINDERS_COMPLETED"), eq(AuditOutcome.PARTIAL), any(), any())
        verify(lease).close()
    }

    @Test
    fun `changes or persistence failures after queueing are recorded and propagated`() {
        whenever(service.send(any(), any(), any())).thenThrow(EventReminderConflictException("Changed"))
        job.send("event", "reviewed", "Edited reminder", "admin@nav.no")
        val task = argumentCaptor<Runnable>()
        verify(executor).execute(task.capture())
        assertThatThrownBy { task.firstValue.run() }.isInstanceOf(EventReminderConflictException::class.java)
        verify(audit).recordRun(eq("EVENT_REMINDERS_FAILED"), eq(AuditOutcome.FAILED), any(), any())
        verify(lease).close()
    }

    @Test
    fun `a running reminder batch does not queue another one`() {
        whenever(lock.tryAcquireLock(any(), any())).thenReturn(null)
        assertThat(job.send("event", "reviewed", "Edited reminder", "admin@nav.no")).isEqualTo(SyncTriggerResult.ALREADY_RUNNING)
        verifyNoInteractions(service, executor)
    }
}
