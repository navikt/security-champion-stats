package navikt.appsec.securitychampionapp.app.feedback

import java.time.Instant
import java.util.UUID

enum class FeedbackStatus { OPEN, IN_PROGRESS, CLOSED }
enum class FeedbackScoreDecision { PENDING, QUALIFIED, NOT_QUALIFIED }
enum class FeedbackAction {
    CLAIM, RELEASE, NOTE, INTERNAL_DISCUSSION, EXTERNAL_TICKET, CONTACT_SUBMITTER,
    CLOSE, REOPEN, QUALIFY, DECLINE,
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
)
data class FeedbackHistory(
    val id: Long,
    val actorEmail: String?,
    val action: FeedbackAction,
    val note: String,
    val ticketUrl: String?,
    val createdAt: Instant,
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
)
data class FeedbackSearch(
    val query: String = "",
    val status: FeedbackStatus? = null,
    val owner: String = "all",
    val scoreDecision: FeedbackScoreDecision? = null,
    val offset: Int = 0,
)
data class FeedbackPage(val items: List<Feedback>, val nextOffset: Int?, val actorEmail: String)

interface FeedbackStore {
    fun currentSeason(): UUID
    fun lockParticipant(id: UUID)
    fun activeParticipant(email: String): UUID?
    fun submit(participantId: UUID, seasonId: UUID, request: SubmitFeedbackRequest, at: Instant)
    fun find(id: UUID, lock: Boolean = false): Feedback?
    fun search(search: FeedbackSearch, actorEmail: String): List<Feedback>
    fun history(id: UUID): List<FeedbackHistory>
    fun award(feedback: Feedback): UUID
    fun update(feedback: Feedback, request: FeedbackActionRequest, actor: String, at: Instant)
}
