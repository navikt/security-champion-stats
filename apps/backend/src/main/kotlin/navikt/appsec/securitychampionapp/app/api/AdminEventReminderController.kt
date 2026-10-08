package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.events.EventReminderPreview
import navikt.appsec.securitychampionapp.app.events.EventReminderService
import navikt.appsec.securitychampionapp.app.jobs.EventReminderJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

data class SendEventRemindersRequest(val expectedVersion: String, val message: String, val confirmed: Boolean = false)

@RestController
@RequestMapping("/api/admin/events/{eventId}/reminders")
class AdminEventReminderController(
    private val service: EventReminderService,
    private val job: EventReminderJob,
) {
    @GetMapping
    fun preview(@PathVariable eventId: String): ResponseEntity<EventReminderPreview> =
        ResponseEntity.ok().header("Cache-Control", "no-store").body(service.preview(eventId))

    @PostMapping
    fun send(@PathVariable eventId: String, @RequestBody request: SendEventRemindersRequest): ResponseEntity<Void> {
        if (!request.confirmed || request.expectedVersion.isBlank()) {
            throw ApiRequestException(HttpStatus.BAD_REQUEST, "Confirmation required", "Preview and confirm the reminder recipients")
        }
        val actor = (requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal).email
        return when (job.send(eventId, request.expectedVersion, request.message, actor)) {
            SyncTriggerResult.STARTED -> ResponseEntity.accepted().build()
            SyncTriggerResult.ALREADY_RUNNING -> throw ApiRequestException(
                HttpStatus.CONFLICT, "Reminders already running", "Wait for the reminder batch to finish before sending again",
            )
            SyncTriggerResult.UNAVAILABLE, SyncTriggerResult.DISABLED -> throw ApiRequestException(
                HttpStatus.SERVICE_UNAVAILABLE, "Reminders unavailable", "The reminder batch could not be started",
            )
        }
    }
}
