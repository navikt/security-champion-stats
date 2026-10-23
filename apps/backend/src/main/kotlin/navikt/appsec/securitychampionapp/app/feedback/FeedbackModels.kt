package navikt.appsec.securitychampionapp.app.feedback

import java.time.Instant
import java.util.UUID

enum class FeedbackStatus { OPEN, IN_PROGRESS, CLOSED }
enum class FeedbackScoreDecision { PENDING, QUALIFIED, NOT_QUALIFIED }
enum class FeedbackAction {
    CLAIM, RELEASE, NOTE, INTERNAL_DISCUSSION, EXTERNAL_TICKET, CONTACT_SUBMITTER,
    CLOSE, REOPEN, QUALIFY, DECLINE, TAKE_OVER, STATUS,
}
enum class FeedbackFailure { INVALID, FORBIDDEN, CONFLICT, NOT_FOUND }
class FeedbackException(val failure: FeedbackFailure, message: String) : RuntimeException(message)

data class SubmitFeedbackRequest(val id: UUID, val title: String, val description: String)
data class FeedbackReceipt(val id: UUID)
data class FeedbackActionRequest(
    val expectedVersion: Long,
    val action: FeedbackAction,
    val note: String = "",
    val ticketUrl: String? = null,
    val status: FeedbackStatus? = null,
)
data class FeedbackHistory(
    val id: Long,
    val actorEmail: String?,
    val action: FeedbackAction,
    val note: String,
    val ticketUrl: String?,
    val createdAt: Instant,
    val actorName: String? = null,
    val previousOwnerName: String? = null,
    val status: FeedbackStatus? = null,
)
data class Feedback(
    val id: UUID,
    val submitterId: UUID,
    val submitterName: String,
    val submitterEmail: String,
    val submitterActive: Boolean,
    val seasonId: UUID,
    val title: String,
    val description: String,
    val status: FeedbackStatus,
    val ownerEmail: String?,
    val scoreDecision: FeedbackScoreDecision,
    val creditId: UUID?,
    val version: Long,
    val createdAt: Instant,
    val history: List<FeedbackHistory> = emptyList(),
    val ownerName: String? = null,
    val readByMe: Boolean = false,
    val awardedPoints: Int? = null,
)
data class FeedbackSearch(
    val query: String = "",
    val status: FeedbackStatus? = null,
    val owner: String = "all",
    val scoreDecision: FeedbackScoreDecision? = null,
    val cursor: String? = null,
    val activeOnly: Boolean = false,
)
data class FeedbackCounts(
    val active: Int = 0,
    val open: Int = 0,
    val inProgress: Int = 0,
    val resolved: Int = 0,
    val all: Int = 0,
    val unread: Int = 0,
    val unassigned: Int = 0,
    val mine: Int = 0,
)
data class FeedbackCursor(val at: Instant, val id: UUID) {
    override fun toString() = "$at|$id"
}
data class FeedbackPage(
    val items: List<Feedback>,
    val nextCursor: String?,
    val actorEmail: String,
    val counts: FeedbackCounts = FeedbackCounts(),
)

interface FeedbackStore {
    fun currentSeason(): UUID
    fun lockParticipant(id: UUID)
    fun activeParticipant(email: String): UUID?
    fun submit(participantId: UUID, seasonId: UUID, request: SubmitFeedbackRequest, at: Instant)
    fun find(id: UUID, lock: Boolean = false, actorEmail: String? = null): Feedback?
    fun search(search: FeedbackSearch, actorEmail: String, cursor: FeedbackCursor?): List<Feedback>
    fun counts(actorEmail: String): FeedbackCounts
    fun markRead(id: UUID, actorEmail: String, at: Instant)
    fun history(id: UUID): List<FeedbackHistory>
    fun award(feedback: Feedback): UUID
    fun update(feedback: Feedback, request: FeedbackActionRequest, actor: String, at: Instant, previousOwner: String?)
}
