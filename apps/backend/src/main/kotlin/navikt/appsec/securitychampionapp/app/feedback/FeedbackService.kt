package navikt.appsec.securitychampionapp.app.feedback

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.Instant
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
        if (search.query.length > 200 || search.owner !in setOf("all", "mine", "unassigned")) {
            fail(FeedbackFailure.INVALID, "Use a search of up to 200 characters and valid filters")
        }
        val cursor = search.cursor?.let {
            if (it.length > 100) fail(FeedbackFailure.INVALID, "Invalid feedback cursor")
            try {
                val parts = it.split('|', limit = 2)
                if (parts.size != 2) fail(FeedbackFailure.INVALID, "Invalid feedback cursor")
                FeedbackCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]))
            } catch (_: java.time.format.DateTimeParseException) {
                fail(FeedbackFailure.INVALID, "Invalid feedback cursor")
            } catch (_: IllegalArgumentException) {
                fail(FeedbackFailure.INVALID, "Invalid feedback cursor")
            }
        }
        val items = store.search(search.copy(query = search.query.trim()), actor, cursor)
        val visible = items.take(50)
        return FeedbackPage(
            visible,
            if (items.size > 50) visible.last().let { FeedbackCursor(it.createdAt, it.id).toString() } else null,
            actor, store.counts(actor),
        )
    }

    @Transactional(readOnly = true)
    fun detail(id: UUID, actor: String? = null): Feedback {
        val feedback = store.find(id, actorEmail = actor) ?: fail(FeedbackFailure.NOT_FOUND, "The feedback does not exist")
        return feedback.copy(history = store.history(id))
    }

    @Transactional
    fun markRead(id: UUID, actor: String) {
        store.find(id, lock = true) ?: fail(FeedbackFailure.NOT_FOUND, "The feedback does not exist")
        store.markRead(id, actor, clock.instant())
    }

    @Transactional
    fun act(id: UUID, actor: String, request: FeedbackActionRequest): Feedback {
        var normalized = validate(request)
        // Match scoring's lock order: configuration, participant, then the feedback record.
        ledger.configuration()
        val initial = store.find(id) ?: fail(FeedbackFailure.NOT_FOUND, "The feedback does not exist")
        store.lockParticipant(initial.submitterId)
        var feedback = store.find(id, lock = true) ?: fail(FeedbackFailure.NOT_FOUND, "The feedback does not exist")
        if (feedback.version != request.expectedVersion) {
            fail(FeedbackFailure.CONFLICT, "The feedback changed. Reload it before continuing")
        }
        val previousOwner = feedback.ownerEmail
        val ownsFeedback = feedback.ownerEmail.equals(actor, ignoreCase = true)
        when (request.action) {
            FeedbackAction.CLAIM, FeedbackAction.TAKE_OVER -> {
                if (ownsFeedback) conflict("You already own this feedback")
                normalized = normalized.copy(action = if (previousOwner == null) FeedbackAction.CLAIM else FeedbackAction.TAKE_OVER)
                feedback = feedback.copy(
                    status = if (feedback.status == FeedbackStatus.OPEN) FeedbackStatus.IN_PROGRESS else feedback.status,
                    ownerEmail = actor,
                )
            }
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
            else -> {
                if (!ownsFeedback) {
                    fail(FeedbackFailure.FORBIDDEN, "Take ownership before handling this feedback")
                }
                when (request.action) {
                    FeedbackAction.RELEASE -> feedback = feedback.copy(ownerEmail = null)
                    FeedbackAction.CLOSE -> feedback = feedback.copy(status = FeedbackStatus.CLOSED)
                    FeedbackAction.REOPEN -> feedback = feedback.copy(status = FeedbackStatus.OPEN)
                    FeedbackAction.STATUS -> feedback = feedback.copy(status = requireNotNull(normalized.status))
                    else -> Unit
                }
            }
        }
        store.update(feedback, normalized, actor, clock.instant(), previousOwner.takeIf { normalized.action == FeedbackAction.TAKE_OVER })
        audit.record(
            "FEEDBACK_${normalized.action}", AuditOutcome.SUCCEEDED, actor, feedback.submitterId,
            details = mapOf("feedbackId" to id.toString()),
        )
        return detail(id, actor)
    }

    private fun validate(request: FeedbackActionRequest): FeedbackActionRequest {
        val note = request.note.trim()
        if (note.length > 3000 || (request.action in setOf(
                FeedbackAction.NOTE, FeedbackAction.INTERNAL_DISCUSSION, FeedbackAction.CONTACT_SUBMITTER,
            ) && note.isBlank())) {
            fail(FeedbackFailure.INVALID, "Provide a note of up to 3000 characters")
        }
        if ((request.action == FeedbackAction.STATUS) != (request.status != null)) {
            fail(FeedbackFailure.INVALID, "Provide a status only for a status change")
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
