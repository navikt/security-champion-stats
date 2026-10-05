package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Component

enum class SyncTriggerResult {
    STARTED,
    ALREADY_RUNNING,
    UNAVAILABLE,
    DISABLED,
}

@Component
class ScoringSyncTrigger(
    private val jobLock: PostgresJobLock,
    @Qualifier("scoringSyncExecutor")
    private val executor: TaskExecutor,
) {
    private val logger = LoggerFactory.getLogger(ScoringSyncTrigger::class.java)

    fun trigger(lockKey: Long, jobName: String, operation: () -> Unit): SyncTriggerResult {
        val lease = jobLock.tryAcquireLock(lockKey, jobName)
            ?: return SyncTriggerResult.ALREADY_RUNNING

        return try {
            executor.execute { lease.use { operation() } }
            SyncTriggerResult.STARTED
        } catch (e: TaskRejectedException) {
            lease.close()
            logger.warn("Could not queue $jobName because the sync executor is full")
            SyncTriggerResult.UNAVAILABLE
        }
    }
}
