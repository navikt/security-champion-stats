package navikt.appsec.securitychampionapp.app.events

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ContributionStatus { PENDING, APPROVED, REJECTED, REVOKED }

enum class EventClaimFailure { INVALID, FORBIDDEN, CONFLICT, NOT_FOUND }

class EventClaimException(val failure: EventClaimFailure, message: String) : RuntimeException(message)

data class EventClaimContributorRequest(val participantId: UUID, val contribution: String)

data class EventClaimRequest(
    val name: String,
    val description: String,
    val startDate: Instant,
    val endDate: Instant,
    val location: String,
    val type: String,
    val externalEvent: Boolean,
    val links: List<String>,
    val invitationEvidence: String,
    val contributors: List<EventClaimContributorRequest>,
    val eventId: UUID? = null,
    val expectedVersion: Long? = null,
)

data class EventClaimContributor(
    val participantId: UUID,
    val fullName: String,
    val contribution: String,
    val status: ContributionStatus,
    val creditId: UUID?,
)

data class EventClaimReview(
    val participantId: UUID,
    val fullName: String,
    val decision: ContributionStatus,
    val reason: String,
    val createdAt: Instant,
)

data class EventClaim(
    val id: UUID,
    val submitterId: UUID,
    val seasonId: UUID,
    val seasonStartsOn: LocalDate,
    val version: Long,
    val eventId: UUID?,
    val published: Boolean,
    val name: String,
    val description: String,
    val startDate: Instant,
    val endDate: Instant,
    val location: String,
    val type: String,
    val externalEvent: Boolean,
    val links: List<String>,
    val invitationEvidence: String,
    val contributors: List<EventClaimContributor>,
    val reviews: List<EventClaimReview>,
) {
    val editable: Boolean
        get() = !published && contributors.none { it.status == ContributionStatus.APPROVED || it.status == ContributionStatus.REVOKED }
}

data class ClaimParticipant(val id: UUID, val fullName: String)

data class EventClaimOverview(
    val currentParticipantId: UUID?,
    val seasonStartsOn: LocalDate,
    val points: Int,
    val participants: List<ClaimParticipant>,
    val claims: List<EventClaim>,
)

data class EventClaimReviewRequest(
    val participantId: UUID,
    val decision: ContributionStatus,
    val reason: String,
    val expectedVersion: Long,
)

interface EventClaimStore {
    fun lockScoring()
    fun currentSeason(): Pair<UUID, LocalDate>
    fun list(participantId: UUID? = null): List<EventClaim>
    fun find(id: UUID, lock: Boolean = false): EventClaim?
    fun eligible(participantId: UUID, eventAt: Instant): Boolean
    fun validateExistingEvent(request: EventClaimRequest): Boolean
    fun save(id: UUID, submitterId: UUID, seasonId: UUID, request: EventClaimRequest, updating: Boolean)
    fun award(claim: EventClaim, participantId: UUID): UUID
    fun revoke(creditId: UUID): Int
    fun publish(claim: EventClaim)
    fun review(claimId: UUID, request: EventClaimReviewRequest, actorEmail: String, creditId: UUID?)
}
