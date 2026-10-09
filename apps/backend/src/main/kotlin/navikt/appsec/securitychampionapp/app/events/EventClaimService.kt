package navikt.appsec.securitychampionapp.app.events

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

@Service
class EventClaimService(
    private val store: EventClaimStore,
    private val participants: ParticipantStore,
    private val ledger: ScoringLedger,
    private val scoring: ScoringService,
    private val audit: ProgramAuditService,
    private val clock: Clock,
) {
    @Transactional
    fun overview(email: String, admin: Boolean): EventClaimOverview {
        val participant = participants.findByNavNoEmail(email)
        if (!admin && participant?.status != ParticipationStatus.ACTIVE) forbidden()
        return EventClaimOverview(
            participant?.id,
            store.currentSeason().second,
            ledger.configuration().activities.single { it.creditType == ActivityCreditType.SECURITY_EVENT_CONTRIBUTION }.points,
            participants.findActiveParticipants().map { ClaimParticipant(it.id, it.fullname) },
            store.list(if (admin) null else requireNotNull(participant).id),
        )
    }

    @Transactional
    fun submit(email: String, request: EventClaimRequest, id: UUID? = null): EventClaim {
        val participant = participants.findByNavNoEmail(email)
            ?.takeIf { it.status == ParticipationStatus.ACTIVE } ?: forbidden()
        store.lockScoring()
        val (currentSeasonId, startsOn) = store.currentSeason()
        val existing = id?.let { store.find(it, lock = true) ?: notFound() }
        if (existing != null) {
            if (existing.submitterId != participant.id) forbidden()
            if (!existing.editable) conflict("Approved claims are locked")
            if (request.expectedVersion != existing.version) conflict("The claim changed. Reload it before editing")
            if (request.startDate != existing.startDate || request.endDate != existing.endDate || request.eventId != existing.eventId) {
                invalid("Event dates and the linked event cannot be changed after submission")
            }
        }
        val normalized = validate(request)
        if (existing == null && request.startDate.atZone(OSLO).toLocalDate().isBefore(startsOn)) {
            invalid("Only events in the current season can be submitted")
        }
        if (participant.id !in normalized.contributors.map { it.participantId }) {
            invalid("Include your own contribution in the claim")
        }
        if (normalized.contributors.any { !store.eligible(it.participantId, normalized.startDate) }) {
            invalid("Every contributor must be active and have enrolled before the event")
        }
        if (normalized.eventId != null && !store.validateExistingEvent(normalized)) {
            invalid("The linked event must match the submitted event details")
        }
        val claimId = id ?: UUID.randomUUID()
        store.save(claimId, participant.id, existing?.seasonId ?: currentSeasonId, normalized, existing != null)
        audit.record(
            "EVENT_CLAIM_SUBMITTED", AuditOutcome.SUCCEEDED, email, participant.id,
            details = mapOf(
                "claimId" to claimId.toString(), "sourceName" to normalized.name,
                "sourceOccurredAt" to normalized.startDate.toString(), "sourceUrl" to normalized.links.first(),
            ),
        )
        return requireNotNull(store.find(claimId))
    }

    @Transactional
    fun review(id: UUID, email: String, request: EventClaimReviewRequest): EventClaim {
        store.lockScoring()
        val claim = store.find(id, lock = true) ?: notFound()
        if (request.expectedVersion != claim.version) conflict("The claim changed. Reload it before reviewing")
        if (request.reason.isBlank() || request.reason.length > 1000) invalid("Provide a review reason of up to 1000 characters")
        val contributor = claim.contributors.singleOrNull { it.participantId == request.participantId }
            ?: invalid("The contributor is not part of this claim")
        val actor = participants.findByNavNoEmail(email)
        if (request.decision == ContributionStatus.APPROVED && actor?.id == contributor.participantId) {
            forbidden("Another administrator must approve your own credit")
        }
        var creditId = contributor.creditId
        when (request.decision) {
            ContributionStatus.APPROVED -> {
                if (contributor.status != ContributionStatus.PENDING) conflict("Only pending contributions can be approved")
                if (!store.eligible(contributor.participantId, claim.startDate)) {
                    conflict("The contributor is no longer eligible")
                }
                creditId = store.award(claim, contributor.participantId)
                audit.record(
                    "CREDIT_AWARDED", AuditOutcome.SUCCEEDED, email, contributor.participantId,
                    details = mapOf(
                        "creditType" to ActivityCreditType.SECURITY_EVENT_CONTRIBUTION.name,
                        "points" to ledger.creditPoints(contributor.participantId, ActivityCreditType.SECURITY_EVENT_CONTRIBUTION, "event-claim:${claim.id}"),
                        "sourceReference" to "event-claim:${claim.id}",
                        "sourceName" to claim.name,
                        "sourceOccurredAt" to claim.startDate.toString(),
                        "sourceUrl" to claim.links.first(),
                    ),
                )
                if (!claim.published) store.publish(claim)
            }
            ContributionStatus.REJECTED -> {
                if (contributor.status != ContributionStatus.PENDING) conflict("Only pending contributions can be rejected")
            }
            ContributionStatus.REVOKED -> {
                if (contributor.status != ContributionStatus.APPROVED || creditId == null) {
                    conflict("Only approved contributions can be revoked")
                }
                val points = store.revoke(creditId)
                if (points != 0) {
                    scoring.addAdjustment(contributor.participantId, -points, request.reason.trim(), email, creditId)
                }
            }
            ContributionStatus.PENDING -> invalid("Choose approve, reject or revoke")
        }
        store.review(id, request.copy(reason = request.reason.trim()), email, creditId)
        audit.record(
            "EVENT_CLAIM_${request.decision}", AuditOutcome.SUCCEEDED, email, contributor.participantId,
            details = mapOf(
                "claimId" to id.toString(), "reason" to request.reason.trim(), "creditId" to creditId?.toString(),
                "sourceName" to claim.name, "sourceOccurredAt" to claim.startDate.toString(),
                "sourceUrl" to claim.links.first(),
            ),
        )
        return requireNotNull(store.find(id))
    }

    private fun validate(request: EventClaimRequest): EventClaimRequest {
        if (request.name.trim().length !in 1..100 || request.description.trim().length !in 1..5000 ||
            request.location.trim().length > 100 || request.invitationEvidence.trim().length !in 1..3000
        ) invalid("Provide a name, security-content description and advance invitation evidence within the field limits")
        if (!request.endDate.isAfter(request.startDate) || request.endDate.isAfter(clock.instant())) {
            invalid("The event must have ended, with its end after its start")
        }
        if (request.type !in setOf("meetup", "workshop")) invalid("Choose a valid event type")
        if (request.links.size !in 1..10 || request.links.any { !validLink(it) }) {
            invalid("Provide 1-10 HTTP or HTTPS links of up to 1000 characters, without credentials")
        }
        if (request.contributors.size !in 1..20 ||
            request.contributors.map { it.participantId }.distinct().size != request.contributors.size ||
            request.contributors.any { it.contribution.trim().length !in 1..2000 }
        ) invalid("Provide 1-20 distinct contributors and describe each substantive contribution")
        return request.copy(
            name = request.name.trim(), description = request.description.trim(), location = request.location.trim(),
            invitationEvidence = request.invitationEvidence.trim(), links = request.links.map { it.trim() }.distinct(),
            contributors = request.contributors.map { it.copy(contribution = it.contribution.trim()) },
        )
    }

    private fun validLink(link: String): Boolean {
        if (link.length > 1000) return false
        val uri = try { URI(link.trim()) } catch (_: java.net.URISyntaxException) { return false }
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }

    private fun forbidden(detail: String = "Active program membership is required"): Nothing =
        throw EventClaimException(EventClaimFailure.FORBIDDEN, detail)
    private fun invalid(detail: String): Nothing = throw EventClaimException(EventClaimFailure.INVALID, detail)
    private fun conflict(detail: String): Nothing = throw EventClaimException(EventClaimFailure.CONFLICT, detail)
    private fun notFound(): Nothing = throw EventClaimException(EventClaimFailure.NOT_FOUND, "The claim does not exist")

    private companion object { val OSLO: ZoneId = ZoneId.of("Europe/Oslo") }
}
