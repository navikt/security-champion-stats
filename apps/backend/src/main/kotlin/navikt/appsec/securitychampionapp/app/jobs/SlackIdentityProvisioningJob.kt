package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.membership.SlackIdentityProvisioningService
import navikt.appsec.securitychampionapp.app.participation.ParticipantEnrolledEvent
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

private const val SLACK_IDENTITY_LOCK_KEY = 1_010L

@Component
class SlackIdentityProvisioningJob(
    private val service: SlackIdentityProvisioningService,
    private val lock: PostgresJobLock,
    @Qualifier("scoringSyncExecutor") private val executor: TaskExecutor,
    private val audit: ProgramAuditService,
) {
    private val logger = LoggerFactory.getLogger(SlackIdentityProvisioningJob::class.java)

    @TransactionalEventListener(fallbackExecution = true)
    fun enrolled(event: ParticipantEnrolledEvent) {
        try {
            executor.execute { run(event.participantId) }
        } catch (_: TaskRejectedException) {
            logger.warn("Slack enrollment lookup deferred because the background executor is busy")
            audit.record(
                "SLACK_IDENTITY_LOOKUP_DEFERRED", AuditOutcome.PARTIAL,
                targetParticipantId = event.participantId,
            )
        }
    }

    @Scheduled(cron = $$"${slack.identity.cron:0 15 */6 * * *}", zone = "Europe/Oslo")
    fun reconcile() = run(null)

    private fun run(participantId: UUID?) {
        val lease = lock.tryAcquireLock(SLACK_IDENTITY_LOCK_KEY, "provisionSlackIdentities")
        if (lease == null) {
            logger.info("Slack identity lookup deferred until scheduled reconciliation because a lookup is running")
            return
        }
        lease.use {
            try {
                val result = service.provision(participantId)
                audit.record(
                    "SLACK_IDENTITY_LOOKUP_COMPLETED",
                    if (result.unresolved == 0) AuditOutcome.SUCCEEDED else AuditOutcome.PARTIAL,
                    targetParticipantId = participantId,
                    details = mapOf("mapped" to result.mapped, "unresolved" to result.unresolved, "skipped" to result.skipped),
                )
                logger.info("Slack identity lookup: mapped={}, unresolved={}, skipped={}", result.mapped, result.unresolved, result.skipped)
            } catch (e: Exception) {
                audit.record("SLACK_IDENTITY_LOOKUP_FAILED", AuditOutcome.FAILED, targetParticipantId = participantId)
                logger.error("Slack identity lookup failed; unresolved participants will be retried during reconciliation", e)
                throw e
            }
        }
    }
}
