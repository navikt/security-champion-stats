package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.core.task.TaskExecutor
import java.util.UUID
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.inOrder

class ScoringSyncTriggerTest {
    @Test
    fun `failed validation closes lease without queueing a write`() {
        val jobLock = mock<PostgresJobLock>()
        val executor = mock<TaskExecutor>()
        val lease = mock<PostgresJobLock.LockLease>()
        whenever(jobLock.tryAcquireLock(7L, "testSync")).thenReturn(lease)
        val trigger = ScoringSyncTrigger(jobLock, executor)
        assertThatThrownBy {
            trigger.triggerValidated(7L, "testSync", "admin@nav.no", { error("stale preview") }) {}
        }.hasMessageContaining("stale preview")
        verify(lease).close()
        verifyNoInteractions(executor)
    }

    @Test
    fun `validation runs under the acquired lease before queueing`() {
        val jobLock = mock<PostgresJobLock>()
        val executor = mock<TaskExecutor>()
        val lease = mock<PostgresJobLock.LockLease>()
        val validator = mock<Runnable>()
        whenever(jobLock.tryAcquireLock(7L, "testSync")).thenReturn(lease)
        val trigger = ScoringSyncTrigger(jobLock, executor)
        assertEquals(SyncTriggerResult.STARTED,
            trigger.triggerValidated(7L, "testSync", "admin@nav.no", { validator.run() }) {})
        val order = inOrder(jobLock, validator, executor)
        order.verify(jobLock).tryAcquireLock(7L, "testSync")
        order.verify(validator).run()
        order.verify(executor).execute(org.mockito.kotlin.any())
    }
    @Test
    fun `should acquire a lock and run the sync in the background`() {
        val jobLock = mock<PostgresJobLock>()
        val executor = mock<TaskExecutor>()
        val lease = mock<PostgresJobLock.LockLease>()
        whenever(jobLock.tryAcquireLock(7L, "testSync")).thenReturn(lease)
        val taskCaptor = argumentCaptor<Runnable>()
        var syncRan = false
        val trigger = ScoringSyncTrigger(jobLock, executor)

        val result = trigger.trigger(7L, "testSync") { syncRan = true }
        verify(executor).execute(taskCaptor.capture())
        taskCaptor.firstValue.run()

        assertEquals(SyncTriggerResult.STARTED, result)
        assertTrue(syncRan)
        verify(lease).close()
    }

    @Test
    fun `should report an existing lock without queueing a sync`() {
        val jobLock = mock<PostgresJobLock>()
        val executor = mock<TaskExecutor>()
        whenever(jobLock.tryAcquireLock(7L, "testSync")).thenReturn(null)
        val trigger = ScoringSyncTrigger(jobLock, executor)

        val result = trigger.trigger(7L, "testSync") {}

        assertEquals(SyncTriggerResult.ALREADY_RUNNING, result)
        verifyNoInteractions(executor)
    }

    @Test
    fun `should retain the manual requester when handing a sync to the executor`() {
        val jobLock = mock<PostgresJobLock>()
        val executor = mock<TaskExecutor>()
        val lease = mock<PostgresJobLock.LockLease>()
        whenever(jobLock.tryAcquireLock(7L, "testSync")).thenReturn(lease)
        val taskCaptor = argumentCaptor<Runnable>()
        var captured: AuditRunContext? = null
        val trigger = ScoringSyncTrigger(jobLock, executor)

        val result = trigger.trigger(7L, "testSync", "admin@nav.no") { captured = it }
        verify(executor).execute(taskCaptor.capture())
        taskCaptor.firstValue.run()

        assertEquals(SyncTriggerResult.STARTED, result)
        assertEquals("admin@nav.no", captured?.actorNavNoEmail)
        assertTrue(captured?.correlationId is UUID)
        verify(lease).close()
    }
}
