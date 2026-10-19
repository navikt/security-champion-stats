package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.events.EventClaimRequest
import navikt.appsec.securitychampionapp.app.events.EventClaimReviewRequest
import navikt.appsec.securitychampionapp.app.events.EventClaimService
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

private fun claimActorEmail(): String =
    (requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal).email

@RestController
@RequestMapping("/api/event-claims")
@Tag(name = "Event claims")
class EventClaimController(private val service: EventClaimService) {
    @GetMapping
    @Operation(summary = "List the current user's event claims")
    fun overview() = service.overview(claimActorEmail(), admin = false)

    @PostMapping
    @Operation(summary = "Submit an event claim")
    fun submit(@RequestBody request: EventClaimRequest) = service.submit(claimActorEmail(), request)

    @PutMapping("/{id}")
    @Operation(summary = "Edit an event claim")
    fun edit(@PathVariable id: UUID, @RequestBody request: EventClaimRequest) =
        service.submit(claimActorEmail(), request, id)
}

@RestController
@RequestMapping("/api/admin/event-claims")
@Tag(name = "Event claims")
class AdminEventClaimController(private val service: EventClaimService) {
    @GetMapping
    @Operation(summary = "List event claims for review")
    fun overview() = service.overview(claimActorEmail(), admin = true)

    @PostMapping("/{id}/reviews")
    @Operation(summary = "Review an event claim")
    fun review(@PathVariable id: UUID, @RequestBody request: EventClaimReviewRequest) =
        service.review(id, claimActorEmail(), request)
}
