package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.integrations.playbook.PlaybookEventClient
import navikt.appsec.securitychampionapp.integrations.postgress.PlaybookEventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val PLAYBOOK_EVENT_IMPORT_LOCK_KEY = 1_006L

@Component
class PlaybookEventImportJob(
    private val eventClient: PlaybookEventClient,
    private val eventRepository: PlaybookEventRepository,
    private val jobLock: PostgresJobLock,
    private val syncTrigger: ScoringSyncTrigger,
    @Value($$"${playbook.events.enabled:false}") private val enabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(PlaybookEventImportJob::class.java)

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 10_000)
    fun importPlaybookEvents() {
        if (!enabled) return
        jobLock.runWithLock(PLAYBOOK_EVENT_IMPORT_LOCK_KEY, "importPlaybookEvents", ::runImport)
    }

    fun triggerManualImport(): SyncTriggerResult {
        if (!enabled) return SyncTriggerResult.DISABLED
        return syncTrigger.trigger(PLAYBOOK_EVENT_IMPORT_LOCK_KEY, "importPlaybookEvents", ::runImport)
    }

    private fun runImport() {
        try {
            val events = eventClient.fetchEvents()
            eventRepository.replaceSnapshot(events)
            logger.info("Playbook event import completed: saved={}", events.size)
        } catch (e: Exception) {
            logger.error("Playbook event import failed; retaining the previous snapshot", e)
        }
    }
}
