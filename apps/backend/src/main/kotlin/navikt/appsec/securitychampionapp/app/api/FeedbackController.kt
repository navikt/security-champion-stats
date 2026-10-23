package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.feedback.*
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

private fun feedbackActor(): String =
    (requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal).email

@RestController
@RequestMapping("/api/feedback")
class FeedbackController(private val service: FeedbackService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(@RequestBody request: SubmitFeedbackRequest) = service.submit(feedbackActor(), request)
}

@RestController
@RequestMapping("/api/admin/feedback")
class AdminFeedbackController(private val service: FeedbackService) {
    @GetMapping
    fun search(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "active") status: String,
        @RequestParam(defaultValue = "all") owner: String,
        @RequestParam(required = false) scoreDecision: FeedbackScoreDecision?,
        @RequestParam(required = false) cursor: String?,
    ): FeedbackPage {
        val selectedStatus = when (status) {
            "active", "all" -> null
            else -> FeedbackStatus.entries.find { it.name == status }
                ?: throw FeedbackException(FeedbackFailure.INVALID, "Select a valid feedback status")
        }
        return service.search(FeedbackSearch(q, selectedStatus, owner, scoreDecision, cursor, status == "active"), feedbackActor())
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID) = service.detail(id, feedbackActor())

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(@PathVariable id: UUID) = service.markRead(id, feedbackActor())

    @PostMapping("/{id}/actions")
    fun act(@PathVariable id: UUID, @RequestBody request: FeedbackActionRequest) = service.act(id, feedbackActor(), request)
}
