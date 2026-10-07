package navikt.appsec.securitychampionapp.app.audit

import navikt.appsec.securitychampionapp.integrations.postgress.ProgramAuditRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.util.UUID

@Service
class ProgramAuditService(
    private val repository: ProgramAuditRepository,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(ProgramAuditService::class.java)

    fun record(
        action: String,
        outcome: AuditOutcome,
        actorNavNoEmail: String? = null,
        targetParticipantId: UUID? = null,
        correlationId: UUID? = null,
        details: Map<String, Any?> = emptyMap(),
    ): Boolean {
        val capturedDetails = details.toMap()
        val write = {
            attempt(action, outcome) {
                val safeTargetId = targetParticipantId?.takeIf(repository::participantExists)
                if (targetParticipantId != null && safeTargetId == null && action != "ADMIN_MUTATION") {
                    logger.warn("Skipping subject-linked audit capture because the participant no longer exists")
                } else {
                    repository.insert(
                        action = action.take(80),
                        outcome = outcome,
                        actorNavNoEmail = actorNavNoEmail,
                        targetParticipantId = safeTargetId,
                        correlationId = correlationId,
                        details = capturedDetails,
                    )
                }
            }
        }
        if (TransactionSynchronizationManager.isActualTransactionActive() &&
            TransactionSynchronizationManager.isSynchronizationActive()
        ) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    write()
                }
            })
            return true
        }
        return write()
    }

    fun recordParticipantEvent(
        participantId: UUID,
        action: String,
        actorNavNoEmail: String? = null,
        outcome: AuditOutcome = AuditOutcome.SUCCEEDED,
        details: Map<String, Any?> = emptyMap(),
    ): Boolean {
        if (action !in PARTICIPANT_HISTORY_ACTIONS) {
            logger.error("Audit event rejected because participant action is not allowlisted")
            return false
        }
        val safeDetails = details
            .filterKeys { it in PARTICIPANT_DETAIL_KEYS }
            .mapValues { (_, value) -> value?.toString()?.take(40).orEmpty() }
        return record(action, outcome, actorNavNoEmail, participantId, details = safeDetails)
    }

    fun recordParticipantDeletion(
        actor: AuditRunContext? = null,
        deletedParticipantId: UUID? = null,
    ): Boolean {
        val retainedActor = if (actor?.actorParticipantId != null &&
            actor.actorParticipantId == deletedParticipantId
        ) {
            null
        } else {
            actor?.let(::retainedActor)
        }
        return record(
            action = "PARTICIPANT_PERMANENTLY_DELETED",
            outcome = AuditOutcome.SUCCEEDED,
            actorNavNoEmail = retainedActor,
            details = emptyMap(),
        )
    }

    fun recordAdminRequest(
        method: String,
        routeTemplate: String?,
        httpStatus: Int,
        actorNavNoEmail: String?,
        targetParticipantId: UUID?,
    ): Boolean {
        val outcome = if (httpStatus in 200..399) AuditOutcome.SUCCEEDED else AuditOutcome.FAILED
        return record(
            action = "ADMIN_MUTATION",
            outcome = outcome,
            actorNavNoEmail = actorNavNoEmail,
            targetParticipantId = targetParticipantId,
            details = mapOf(
                "method" to method,
                "route" to (routeTemplate ?: "unresolved"),
                "httpStatus" to httpStatus,
            ),
        )
    }

    fun recordRun(
        action: String,
        outcome: AuditOutcome,
        run: AuditRunContext,
        details: Map<String, Any?> = emptyMap(),
    ): Boolean {
        return record(
            action = action,
            outcome = outcome,
            actorNavNoEmail = retainedActor(run),
            correlationId = run.correlationId,
            details = details,
        )
    }

    private fun retainedActor(run: AuditRunContext): String? =
        if (run.actorParticipantId == null) {
            run.actorNavNoEmail
        } else {
            try {
                run.actorNavNoEmail.takeIf { repository.participantExists(run.actorParticipantId) }
            } catch (e: Exception) {
                logger.error("Could not resolve async audit actor: {}", e.javaClass.simpleName)
                null
            }
        }

    fun captureRunContext(actorNavNoEmail: String?): AuditRunContext {
        if (actorNavNoEmail == null) return AuditRunContext()
        return try {
            AuditRunContext(
                actorNavNoEmail = actorNavNoEmail,
                actorParticipantId = repository.participantIdForNavNoEmail(actorNavNoEmail),
            )
        } catch (e: Exception) {
            logger.error("Could not resolve requester for async audit capture: {}", e.javaClass.simpleName)
            AuditRunContext()
        }
    }

    fun adminPage(query: String?, category: String?, page: Int, size: Int): ProgramAuditPage {
        val (items, total) = repository.adminPage(query, page, size, category)
        return ProgramAuditPage(items, total, page, size)
    }

    fun participantHistory(participantId: UUID): List<ParticipantHistoryEntry> =
        repository.participantHistory(participantId)

    fun purgeExpiredOperationalEvents() {
        try {
            val deleted = repository.deleteExpiredOperationalEvents(clock.instant())
            if (deleted > 0) logger.info("Purged {} expired operational audit events", deleted)
        } catch (e: Exception) {
            logger.error("Failed to purge expired operational audit events: {}", e.javaClass.simpleName)
        }
    }

    private inline fun attempt(
        action: String,
        outcome: AuditOutcome,
        operation: () -> Unit,
    ): Boolean =
        try {
            operation()
            true
        } catch (e: Exception) {
            logger.error(
                "Best-effort audit recording failed for action={} outcome={} cause={}",
                action,
                outcome,
                e.javaClass.simpleName,
            )
            false
        }

    private companion object {
        val PARTICIPANT_HISTORY_ACTIONS = setOf(
            "PARTICIPANT_ENROLLED",
            "PARTICIPANT_LEFT",
            "PARTICIPANT_REJOINED",
        )
        val PARTICIPANT_DETAIL_KEYS = setOf("status", "previousStatus", "newStatus")
    }
}
