package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.config.SlackMembershipProperties
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.SlackMembershipRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

private const val SLACK_MEMBERSHIP_LOCK_KEY = 1_008L
data class SlackMembershipConfiguration(val enabled: Boolean, val dryRun: Boolean)

@Component
class SlackMembershipSyncJob(
    private val lock: PostgresJobLock,
    private val trigger: ScoringSyncTrigger,
    private val service: SlackMembershipService,
    private val repository: SlackMembershipRepository,
    private val properties: SlackMembershipProperties,
    private val audit: ProgramAuditService,
) {
    private val logger = LoggerFactory.getLogger(SlackMembershipSyncJob::class.java)

    @Scheduled(cron = $$"${slack.membership.cron:0 30 */6 * * *}", zone = "Europe/Oslo")
    fun scheduledSync() {
        if (!properties.enabled) return
        lock.runWithLock(SLACK_MEMBERSHIP_LOCK_KEY, "syncSlackMembership") { runSync(AuditRunContext()) }
    }

    fun triggerManualSync(actor: String): SyncTriggerResult =
        if (!properties.enabled) SyncTriggerResult.DISABLED else
            trigger.trigger(SLACK_MEMBERSHIP_LOCK_KEY, "syncSlackMembership", actor, ::runSync)

    fun preview(): SlackMembershipPreview = service.sync(dryRun = true)

    fun configuration() = SlackMembershipConfiguration(properties.enabled, properties.dryRun)

    fun announcements(): List<MembershipAnnouncement> = repository.announcements(properties.usergroupId)

    fun resolveUncertain(id: UUID, retry: Boolean, actor: String): Boolean {
        val lease = lock.tryAcquireLock(SLACK_MEMBERSHIP_LOCK_KEY, "resolveSlackMembershipDelivery")
            ?: throw MembershipSyncBusyException()
        return lease.use {
            val announcement = announcements().firstOrNull { it.id == id }
            val updated = repository.resolveUncertain(properties.usergroupId, id, retry)
            if (updated) {
                audit.record(
                    "SLACK_MEMBERSHIP_DELIVERY_RESOLVED", AuditOutcome.SUCCEEDED,
                    actorNavNoEmail = actor,
                    targetParticipantId = announcement?.participantId,
                    details = mapOf("deliveryId" to id.toString(), "retry" to retry),
                )
            }
            updated
        }
    }

    private fun runSync(run: AuditRunContext) {
        audit.recordRun("SLACK_MEMBERSHIP_SYNC_STARTED", AuditOutcome.SUCCEEDED, run)
        try {
            val result = service.sync(properties.dryRun)
            val outstanding = announcements()
            val partial = result.unresolvedParticipantIds.isNotEmpty() || outstanding.isNotEmpty()
            audit.recordRun(
                "SLACK_MEMBERSHIP_SYNC_COMPLETED", if (partial) AuditOutcome.PARTIAL else AuditOutcome.SUCCEEDED, run,
                mapOf(
                    "dryRun" to properties.dryRun,
                    "activeParticipants" to result.activeParticipants,
                    "additions" to result.addedUserIds.size,
                    "removals" to result.removedUserIds.size,
                    "unresolvedIdentities" to result.unresolvedParticipantIds.size,
                    "outstandingAnnouncements" to outstanding.size,
                ),
            )
            logger.info(
                "Slack membership sync: dryRun={}, active={}, additions={}, removals={}, unresolved={}, outstanding={}",
                properties.dryRun, result.activeParticipants, result.addedUserIds.size, result.removedUserIds.size,
                result.unresolvedParticipantIds.size, outstanding.size,
            )
            if (partial) logger.warn("Slack membership sync requires follow-up through the admin membership API")
        } catch (e: Exception) {
            audit.recordRun(
                "SLACK_MEMBERSHIP_SYNC_FAILED", AuditOutcome.FAILED, run,
                mapOf("failure" to e.javaClass.simpleName),
            )
            logger.error("Slack membership sync failed", e)
            throw e
        }
    }
}
