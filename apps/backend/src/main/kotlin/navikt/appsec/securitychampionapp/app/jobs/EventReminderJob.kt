package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.events.EventReminderService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EventReminderJob(
    private val service: EventReminderService,
    private val trigger: ScoringSyncTrigger,
    private val audit: ProgramAuditService,
) {
    private val logger = LoggerFactory.getLogger(EventReminderJob::class.java)

    fun send(eventId: String, expectedVersion: String, actor: String): SyncTriggerResult =
        trigger.triggerValidated(
            1_009L, "sendEventReminders", actor,
            { service.validate(eventId, expectedVersion) },
        ) { run ->
            try {
                val result = service.send(eventId, expectedVersion)
                audit.recordRun(
                    "EVENT_REMINDERS_COMPLETED",
                    if (result.failed + result.uncertain + result.skipped == 0) AuditOutcome.SUCCEEDED else AuditOutcome.PARTIAL,
                    run,
                    mapOf(
                        "eventId" to eventId, "sent" to result.sent, "failed" to result.failed,
                        "uncertain" to result.uncertain, "skipped" to result.skipped,
                    ),
                )
            } catch (e: Exception) {
                audit.recordRun("EVENT_REMINDERS_FAILED", AuditOutcome.FAILED, run, mapOf("eventId" to eventId))
                logger.error("Event reminder batch failed (event={})", eventId, e)
                throw e
            }
        }
}
