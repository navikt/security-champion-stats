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
        @RequestParam(required = false) status: FeedbackStatus?,
        @RequestParam(defaultValue = "all") owner: String,
        @RequestParam(required = false) scoreDecision: FeedbackScoreDecision?,
        @RequestParam(defaultValue = "0") offset: Int,
    ) = service.search(FeedbackSearch(q, status, owner, scoreDecision, offset), feedbackActor())

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID) = service.detail(id)

    @PostMapping("/{id}/actions")
    fun act(@PathVariable id: UUID, @RequestBody request: FeedbackActionRequest) = service.act(id, feedbackActor(), request)
}
