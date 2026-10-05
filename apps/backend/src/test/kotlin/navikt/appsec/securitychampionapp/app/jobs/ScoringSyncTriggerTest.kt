package navikt.appsec.securitychampionapp.app.jobs

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

class ScoringSyncTriggerTest {
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
}
