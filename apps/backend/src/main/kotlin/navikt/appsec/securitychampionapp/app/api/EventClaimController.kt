package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.events.EventClaimRequest
import navikt.appsec.securitychampionapp.app.events.EventClaimReviewRequest
import navikt.appsec.securitychampionapp.app.events.EventClaimService
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

private fun claimActorEmail(): String =
    (requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal).email

@RestController
@RequestMapping("/api/event-claims")
class EventClaimController(private val service: EventClaimService) {
    @GetMapping
    fun overview() = service.overview(claimActorEmail(), admin = false)

    @PostMapping
    fun submit(@RequestBody request: EventClaimRequest) = service.submit(claimActorEmail(), request)

    @PutMapping("/{id}")
    fun edit(@PathVariable id: UUID, @RequestBody request: EventClaimRequest) =
        service.submit(claimActorEmail(), request, id)
}

@RestController
@RequestMapping("/api/admin/event-claims")
class AdminEventClaimController(private val service: EventClaimService) {
    @GetMapping
    fun overview() = service.overview(claimActorEmail(), admin = true)

    @PostMapping("/{id}/reviews")
    fun review(@PathVariable id: UUID, @RequestBody request: EventClaimReviewRequest) =
        service.review(id, claimActorEmail(), request)
}
