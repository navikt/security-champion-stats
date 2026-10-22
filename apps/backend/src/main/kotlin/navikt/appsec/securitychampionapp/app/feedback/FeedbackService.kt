package navikt.appsec.securitychampionapp.app.feedback

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.util.UUID

@Service
class FeedbackService(
    private val store: FeedbackStore,
    private val ledger: ScoringLedger,
    private val audit: ProgramAuditService,
    private val clock: Clock,
) {
    @Transactional
    fun submit(email: String, request: SubmitFeedbackRequest): FeedbackReceipt {
        val normalized = request.copy(title = request.title.trim(), description = request.description.trim())
        if (normalized.title.length !in 1..200 || normalized.description.length !in 1..5000) {
            fail(FeedbackFailure.INVALID, "Provide a title of up to 200 characters and feedback of up to 5000 characters")
        }
        ledger.configuration()
        val seasonId = store.currentSeason()
        val participantId = store.activeParticipant(email)
            ?: fail(FeedbackFailure.FORBIDDEN, "Active program membership is required")
        // A client-generated ID makes a retry safe after a lost submission response.
        store.submit(participantId, seasonId, normalized, clock.instant())
        val saved = store.find(request.id) ?: error("Submitted feedback is missing")
        if (saved.submitterId != participantId || saved.title != normalized.title || saved.description != normalized.description) {
            fail(FeedbackFailure.CONFLICT, "The submission ID is already in use")
        }
        return FeedbackReceipt(saved.id)
    }

    @Transactional(readOnly = true)
    fun search(search: FeedbackSearch, actor: String): FeedbackPage {
        if (search.query.length > 200 || search.offset !in 0..100000 || search.owner !in setOf("all", "mine", "unassigned")) {
            fail(FeedbackFailure.INVALID, "Use a search of up to 200 characters and valid filters")
        }
        val items = store.search(search.copy(query = search.query.trim()), actor)
        return FeedbackPage(items.take(25), if (items.size > 25) search.offset + 25 else null, actor)
    }

    @Transactional(readOnly = true)
    fun detail(id: UUID): Feedback {
        val feedback = store.find(id) ?: fail(FeedbackFailure.NOT_FOUND, "The feedback does not exist")
        return feedback.copy(history = store.history(id))
    }

    @Transactional
    fun act(id: UUID, actor: String, request: FeedbackActionRequest): Feedback {
        val normalized = validate(request)
        // Match scoring's lock order: configuration, participant, then the feedback record.
        ledger.configuration()
        val initial = store.find(id) ?: fail(FeedbackFailure.NOT_FOUND, "The feedback does not exist")
        store.lockParticipant(initial.submitterId)
        var feedback = store.find(id, lock = true) ?: fail(FeedbackFailure.NOT_FOUND, "The feedback does not exist")
        if (feedback.version != request.expectedVersion) {
            fail(FeedbackFailure.CONFLICT, "The feedback changed. Reload it before continuing")
        }
        when (request.action) {
            FeedbackAction.CLAIM -> {
                if (feedback.status != FeedbackStatus.OPEN) conflict("Only unassigned open feedback can be claimed")
                feedback = feedback.copy(status = FeedbackStatus.IN_PROGRESS, ownerEmail = actor)
            }
            FeedbackAction.REOPEN -> {
                if (feedback.status != FeedbackStatus.CLOSED) conflict("Only closed feedback can be reopened")
                feedback = feedback.copy(status = FeedbackStatus.OPEN, ownerEmail = null)
            }
            else -> {
                if (feedback.status != FeedbackStatus.IN_PROGRESS || feedback.ownerEmail != actor) {
                    conflict("Take ownership before handling this feedback")
                }
                when (request.action) {
                    FeedbackAction.RELEASE -> feedback = feedback.copy(status = FeedbackStatus.OPEN, ownerEmail = null)
                    FeedbackAction.CLOSE -> feedback = feedback.copy(status = FeedbackStatus.CLOSED, ownerEmail = null)
                    FeedbackAction.QUALIFY, FeedbackAction.DECLINE -> {
                        if (feedback.scoreDecision != FeedbackScoreDecision.PENDING) conflict("A scoring decision has already been recorded")
                        if (request.action == FeedbackAction.QUALIFY) {
                            if (feedback.submitterEmail.equals(actor, ignoreCase = true)) {
                                fail(FeedbackFailure.FORBIDDEN, "Another administrator must award your feedback credit")
                            }
                            if (!feedback.submitterActive) conflict("The submitter is no longer an active participant")
                            feedback = feedback.copy(scoreDecision = FeedbackScoreDecision.QUALIFIED, creditId = store.award(feedback))
                            audit.record(
                                "CREDIT_AWARDED", AuditOutcome.SUCCEEDED, actor, feedback.submitterId,
                                details = mapOf(
                                    "creditType" to ActivityCreditType.FEEDBACK.name,
                                    "points" to ledger.creditPoints(feedback.submitterId, ActivityCreditType.FEEDBACK, "feedback:$id"),
                                    "sourceReference" to "feedback:$id",
                                ),
                            )
                        } else {
                            feedback = feedback.copy(scoreDecision = FeedbackScoreDecision.NOT_QUALIFIED)
                        }
                    }
                    else -> Unit
                }
            }
        }
        store.update(feedback, normalized, actor, clock.instant())
        audit.record(
            "FEEDBACK_${request.action}", AuditOutcome.SUCCEEDED, actor, feedback.submitterId,
            details = mapOf("feedbackId" to id.toString()),
        )
        return detail(id)
    }

    private fun validate(request: FeedbackActionRequest): FeedbackActionRequest {
        val note = request.note.trim()
        if (note.length > 3000 || (request.action !in setOf(FeedbackAction.CLAIM, FeedbackAction.RELEASE, FeedbackAction.REOPEN) && note.isBlank())) {
            fail(FeedbackFailure.INVALID, "Provide a note of up to 3000 characters")
        }
        val url = request.ticketUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (request.action == FeedbackAction.EXTERNAL_TICKET) {
            val uri = try { url?.let(::URI) } catch (_: java.net.URISyntaxException) { null }
            if (url == null || url.length > 1000 || uri == null || uri.scheme !in setOf("http", "https") ||
                uri.host.isNullOrBlank() || uri.userInfo != null
            ) fail(FeedbackFailure.INVALID, "Provide an HTTP or HTTPS ticket URL without credentials, up to 1000 characters")
        } else if (url != null) {
            fail(FeedbackFailure.INVALID, "Ticket links belong to external-ticket actions")
        }
        return request.copy(note = note, ticketUrl = url)
    }

    private fun conflict(message: String): Nothing = fail(FeedbackFailure.CONFLICT, message)
    private fun fail(failure: FeedbackFailure, message: String): Nothing = throw FeedbackException(failure, message)
}
