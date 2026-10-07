package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Component
import java.util.UUID

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
    private val auditService: ProgramAuditService? = null,
) {
    private val logger = LoggerFactory.getLogger(ScoringSyncTrigger::class.java)

    fun trigger(lockKey: Long, jobName: String, operation: () -> Unit): SyncTriggerResult {
        return triggerInternal(lockKey, jobName, AuditRunContext(), operation = { operation() })
    }

    fun trigger(
        lockKey: Long,
        jobName: String,
        actorNavNoEmail: String?,
        operation: (AuditRunContext) -> Unit,
    ): SyncTriggerResult =
        triggerInternal(
            lockKey,
            jobName,
            auditService?.captureRunContext(actorNavNoEmail) ?: AuditRunContext(UUID.randomUUID(), actorNavNoEmail),
            operation,
        )

    private fun triggerInternal(
        lockKey: Long,
        jobName: String,
        context: AuditRunContext,
        operation: (AuditRunContext) -> Unit,
        validate: () -> Unit = {},
    ): SyncTriggerResult {
        auditService?.recordRun(
            "SYNC_REQUESTED",
            AuditOutcome.SUCCEEDED,
            context,
            mapOf("job" to jobName),
        )
        val lease = jobLock.tryAcquireLock(lockKey, jobName)
        if (lease == null) {
            auditService?.recordRun(
                "SYNC_REQUEST_REJECTED",
                AuditOutcome.FAILED,
                context,
                mapOf("job" to jobName, "failure" to "alreadyRunning"),
            )
            return SyncTriggerResult.ALREADY_RUNNING
        }

        try {
            validate()
        } catch (e: Exception) {
            lease.close()
            auditService?.recordRun(
                "SYNC_REQUEST_REJECTED", AuditOutcome.FAILED, context,
                mapOf("job" to jobName, "failure" to e.javaClass.simpleName),
            )
            throw e
        }
        return try {
            executor.execute { lease.use { operation(context) } }
            SyncTriggerResult.STARTED
        } catch (e: TaskRejectedException) {
            lease.close()
            auditService?.recordRun(
                "SYNC_REQUEST_REJECTED",
                AuditOutcome.FAILED,
                context,
                mapOf("job" to jobName, "failure" to "executorUnavailable"),
            )
            logger.warn("Could not queue $jobName because the sync executor is full")
            SyncTriggerResult.UNAVAILABLE
        }
    }

    fun triggerValidated(
        lockKey: Long,
        jobName: String,
        actorNavNoEmail: String,
        validate: () -> Unit,
        operation: (AuditRunContext) -> Unit,
    ): SyncTriggerResult = triggerInternal(
        lockKey, jobName,
        auditService?.captureRunContext(actorNavNoEmail) ?: AuditRunContext(UUID.randomUUID(), actorNavNoEmail),
        operation, validate,
    )
}
