package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.playbook.PlaybookEventClient
import navikt.appsec.securitychampionapp.integrations.postgress.PlaybookEventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

private const val PLAYBOOK_EVENT_IMPORT_LOCK_KEY = 1_006L
private const val PLAYBOOK_EVENT_IMPORT_INTERVAL_MILLIS = 21_600_000L
private val PLAYBOOK_EVENT_IMPORT_INTERVAL = Duration.ofMillis(PLAYBOOK_EVENT_IMPORT_INTERVAL_MILLIS)

@Component
class PlaybookEventImportJob(
    private val eventClient: PlaybookEventClient,
    private val eventRepository: PlaybookEventRepository,
    private val jobLock: PostgresJobLock,
    private val syncTrigger: ScoringSyncTrigger,
    @Value($$"${playbook.events.enabled:false}") private val enabled: Boolean,
    private val auditService: ProgramAuditService? = null,
) {
    private val logger = LoggerFactory.getLogger(PlaybookEventImportJob::class.java)

    @Scheduled(fixedDelay = PLAYBOOK_EVENT_IMPORT_INTERVAL_MILLIS, initialDelay = 10_000)
    fun importPlaybookEvents() {
        if (!enabled) return
        jobLock.runWithLockAtMostOncePerInterval(
            PLAYBOOK_EVENT_IMPORT_LOCK_KEY,
            "importPlaybookEvents",
            PLAYBOOK_EVENT_IMPORT_INTERVAL,
        ) {
            runImport(AuditRunContext())
        }
    }

    fun triggerManualImport(actorNavNoEmail: String? = null): SyncTriggerResult {
        if (!enabled) return SyncTriggerResult.DISABLED
        if (actorNavNoEmail == null) {
            return syncTrigger.trigger(PLAYBOOK_EVENT_IMPORT_LOCK_KEY, "importPlaybookEvents") {
                runImport(AuditRunContext())
            }
        }
        return syncTrigger.trigger(PLAYBOOK_EVENT_IMPORT_LOCK_KEY, "importPlaybookEvents", actorNavNoEmail, ::runImport)
    }

    private fun runImport(run: AuditRunContext) {
        auditService?.recordRun("PLAYBOOK_EVENT_IMPORT_STARTED", AuditOutcome.SUCCEEDED, run)
        try {
            val events = eventClient.fetchEvents()
            eventRepository.replaceSnapshot(events)
            auditService?.recordRun(
                "PLAYBOOK_EVENT_IMPORT_COMPLETED",
                AuditOutcome.SUCCEEDED,
                run,
                mapOf("eventsSaved" to events.size),
            )
            logger.info("Playbook event import completed: saved={}", events.size)
        } catch (e: Exception) {
            auditService?.recordRun(
                "PLAYBOOK_EVENT_IMPORT_FAILED",
                AuditOutcome.FAILED,
                run,
                mapOf("failure" to "import"),
            )
            logger.error("Playbook event import failed; retaining the previous snapshot", e)
        }
    }
}
