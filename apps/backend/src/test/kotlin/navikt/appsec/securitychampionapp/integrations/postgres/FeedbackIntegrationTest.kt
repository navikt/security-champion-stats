package navikt.appsec.securitychampionapp.integrations.postgres

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.feedback.*
import navikt.appsec.securitychampionapp.app.scoring.*
import navikt.appsec.securitychampionapp.integrations.postgress.*
import navikt.appsec.securitychampionapp.testsupport.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FeedbackIntegrationTest : PostgresIntegrationTest(maxPoolSize = 6) {
    private lateinit var tx: TransactionTemplate
    private lateinit var store: PostgresFeedbackStore
    private lateinit var ledger: PostgresScoringLedger
    private lateinit var participants: ProgramParticipantRepository
    private lateinit var service: FeedbackService
    private lateinit var participantId: UUID
    private val at = Instant.parse("2026-10-01T10:00:00Z")
    private val admin = "admin@nav.no"
    private val submitter = "participant@nav.no"

    @BeforeEach
    fun setup() {
        tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
        store = PostgresFeedbackStore(jdbcTemplate)
        ledger = PostgresScoringLedger(jdbcTemplate)
        participants = ProgramParticipantRepository(jdbcTemplate)
        service = FeedbackService(store, ledger, mock<ProgramAuditService>(), Clock.fixed(at, ZoneOffset.UTC))
        participants.enroll(submitter, "T12345", submitter, "Participant")
        participantId = requireNotNull(participants.findByNavNoEmail(submitter)).id
        jdbcTemplate.update("UPDATE program_seasons SET starts_on = '2026-01-01' WHERE ends_on IS NULL")
    }

    private fun submit(title: String = "Improve navigation"): Feedback {
        val request = SubmitFeedbackRequest(UUID.randomUUID(), title, "Make program information easier to find")
        tx.executeWithoutResult { service.submit(submitter, request) }
        return service.detail(request.id)
    }

    private fun act(
        feedback: Feedback,
        action: FeedbackAction,
        actor: String = admin,
        note: String = "Verified and recorded",
        url: String? = null,
        status: FeedbackStatus? = null,
    ): Feedback = requireNotNull(tx.execute {
        service.act(feedback.id, actor, FeedbackActionRequest(feedback.version, action, note, url, status))
    })

    @Test
    fun `submission retries save once and only active participants may submit`() {
        val request = SubmitFeedbackRequest(UUID.randomUUID(), "  Better navigation  ", "  Please simplify the menu  ")
        repeat(2) { tx.executeWithoutResult { service.submit(submitter, request) } }
        assertThat(service.search(FeedbackSearch(), admin).items).hasSize(1)
        assertThat(service.detail(request.id).title).isEqualTo("Better navigation")
        assertThat(service.detail(request.id).createdAt).isEqualTo(at)
        assertThat(assertThrows<FeedbackException> {
            tx.execute { service.submit(submitter, request.copy(description = "Different submission")) }
        }.failure).isEqualTo(FeedbackFailure.CONFLICT)
        assertThat(assertThrows<FeedbackException> {
            tx.execute { service.submit("outsider@nav.no", request.copy(id = UUID.randomUUID())) }
        }.failure).isEqualTo(FeedbackFailure.FORBIDDEN)
        participants.leave(submitter)
        assertThat(assertThrows<FeedbackException> {
            tx.execute { service.submit(submitter, request.copy(id = UUID.randomUUID())) }
        }.failure).isEqualTo(FeedbackFailure.FORBIDDEN)
    }

    @Test
    fun `reading leaves ownership unchanged and only one concurrent administrator can claim`() {
        val feedback = submit()
        repeat(2) { assertThat(service.detail(feedback.id).ownerEmail).isNull() }
        assertThat(service.detail(feedback.id).history).isEmpty()
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(admin, "other-admin@nav.no").map { actor ->
                executor.submit<String> {
                    barrier.await(10, TimeUnit.SECONDS)
                    try {
                        act(feedback, FeedbackAction.CLAIM, actor)
                        "claimed"
                    } catch (exception: FeedbackException) {
                        assertThat(exception.failure).isEqualTo(FeedbackFailure.CONFLICT)
                        "conflict"
                    }
                }
            }.map { it.get(15, TimeUnit.SECONDS) }
            assertThat(results).containsExactlyInAnyOrder("claimed", "conflict")
        } finally {
            executor.shutdownNow()
        }
        assertThat(service.detail(feedback.id).history).hasSize(1)
    }

    @Test
    fun `ownership can be released and actions preserve an ordered searchable handling history`() {
        var feedback = act(submit(), FeedbackAction.CLAIM)
        assertThat(assertThrows<FeedbackException> {
            act(feedback, FeedbackAction.NOTE, "other-admin@nav.no")
        }.failure).isEqualTo(FeedbackFailure.FORBIDDEN)
        feedback = act(feedback, FeedbackAction.INTERNAL_DISCUSSION, note = "Discussed onboarding with the team")
        feedback = act(feedback, FeedbackAction.EXTERNAL_TICKET, url = "https://example.org/tickets/42")
        feedback = act(feedback, FeedbackAction.CONTACT_SUBMITTER, note = "Asked the participant for examples")
        feedback = act(feedback, FeedbackAction.RELEASE)
        assertThat(feedback.status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedback.ownerEmail).isNull()
        feedback = act(feedback, FeedbackAction.CLAIM, "other-admin@nav.no")
        feedback = act(feedback, FeedbackAction.CLOSE, "other-admin@nav.no", "Tracked in the external ticket")
        assertThat(feedback.status).isEqualTo(FeedbackStatus.CLOSED)
        assertThat(feedback.scoreDecision).isEqualTo(FeedbackScoreDecision.PENDING)
        assertThat(feedback.history.map { it.action }).containsExactly(
            FeedbackAction.CLOSE, FeedbackAction.CLAIM, FeedbackAction.RELEASE, FeedbackAction.CONTACT_SUBMITTER,
            FeedbackAction.EXTERNAL_TICKET, FeedbackAction.INTERNAL_DISCUSSION, FeedbackAction.CLAIM,
        )
        assertThat(feedback.history.map { it.createdAt }).containsOnly(at)
        assertThat(service.search(FeedbackSearch(query = "ONBOARDING", status = FeedbackStatus.CLOSED), admin).items).hasSize(1)
        assertThat(service.search(FeedbackSearch(query = "tickets/42"), admin).items).hasSize(1)
        assertThat(service.search(FeedbackSearch(query = submitter), admin).items).hasSize(1)
        assertThat(service.search(FeedbackSearch(status = FeedbackStatus.OPEN), admin).items).isEmpty()
        feedback = act(feedback, FeedbackAction.REOPEN, "other-admin@nav.no")
        assertThat(feedback.status).isEqualTo(FeedbackStatus.OPEN)
        assertThat(ledger.creditsForParticipant(participantId)).isEmpty()
    }

    @Test
    fun `read state is per administrator and changes neither ownership version nor history`() {
        val feedback = submit()
        assertThat(service.search(FeedbackSearch(), admin).counts.unread).isEqualTo(1)
        repeat(2) { tx.executeWithoutResult { service.markRead(feedback.id, admin) } }
        val read = service.detail(feedback.id, admin)
        assertThat(read.readByMe).isTrue()
        assertThat(read.ownerEmail).isNull()
        assertThat(read.version).isEqualTo(feedback.version)
        assertThat(read.history).isEmpty()
        assertThat(service.detail(feedback.id, "other-admin@nav.no").readByMe).isFalse()
        assertThat(service.search(FeedbackSearch(), admin).counts.unread).isZero()
        assertThat(service.search(FeedbackSearch(), "other-admin@nav.no").counts.unread).isEqualTo(1)
    }

    @Test
    fun `takeover status and scoring are independent and only the owner can follow up`() {
        var feedback = submit()
        feedback = act(feedback, FeedbackAction.QUALIFY, note = "")
        assertThat(feedback.ownerEmail).isNull()
        assertThat(feedback.status).isEqualTo(FeedbackStatus.OPEN)
        assertThat(feedback.awardedPoints).isEqualTo(1)
        feedback = act(feedback, FeedbackAction.CLAIM, note = "")
        assertThat(feedback.status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(service.search(FeedbackSearch(), admin).counts).isEqualTo(
            FeedbackCounts(active = 1, inProgress = 1, all = 1, unread = 1, mine = 1),
        )
        feedback = act(feedback, FeedbackAction.CLAIM, "other-admin@nav.no", note = "")
        assertThat(feedback.history.first().action).isEqualTo(FeedbackAction.TAKE_OVER)
        assertThat(feedback.history.first().previousOwnerName).isEqualTo(admin)
        listOf(FeedbackAction.NOTE, FeedbackAction.EXTERNAL_TICKET, FeedbackAction.STATUS).forEach { action ->
            assertThat(assertThrows<FeedbackException> {
                act(feedback, action, url = if (action == FeedbackAction.EXTERNAL_TICKET) "https://example.org/1" else null,
                    status = if (action == FeedbackAction.STATUS) FeedbackStatus.CLOSED else null)
            }.failure).isEqualTo(FeedbackFailure.FORBIDDEN)
        }
        feedback = act(feedback, FeedbackAction.STATUS, "other-admin@nav.no", note = "", status = FeedbackStatus.CLOSED)
        assertThat(feedback.ownerEmail).isEqualTo("other-admin@nav.no")
        assertThat(feedback.history.first().status).isEqualTo(FeedbackStatus.CLOSED)
        assertThat(service.search(FeedbackSearch(activeOnly = true), admin).items).isEmpty()
        val filtered = service.search(FeedbackSearch(query = "no match"), admin)
        assertThat(filtered.items).isEmpty()
        assertThat(filtered.counts).isEqualTo(FeedbackCounts(resolved = 1, all = 1, unread = 1))
        feedback = act(feedback, FeedbackAction.RELEASE, "other-admin@nav.no", note = "")
        assertThat(feedback.status).isEqualTo(FeedbackStatus.CLOSED)
        assertThat(feedback.ownerEmail).isNull()
        assertThat(ledger.creditsForParticipant(participantId)).hasSize(1)
        assertThrows<FeedbackException> { service.search(FeedbackSearch(cursor = "invalid"), admin) }
    }

    @Test
    fun `qualifying awards configured points once in the submission season independently of handling`() {
        var feedback = act(submit(), FeedbackAction.CLAIM)
        val originalSeason = feedback.seasonId
        tx.executeWithoutResult { ledger.resetManually(LocalDate.parse("2026-10-02"), "Next season", admin) }
        jdbcTemplate.update("UPDATE program_activity_points SET points = 4 WHERE credit_type = 'FEEDBACK'")
        val stale = feedback
        feedback = act(feedback, FeedbackAction.QUALIFY)
        assertThat(feedback.status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedback.scoreDecision).isEqualTo(FeedbackScoreDecision.QUALIFIED)
        assertThat(ledger.scoreForParticipant(participantId, originalSeason)).isEqualTo(4)
        assertThat(ledger.scoreForParticipant(participantId, ledger.currentSeason().id)).isZero()
        assertThrows<FeedbackException> { act(stale, FeedbackAction.QUALIFY) }
        assertThrows<FeedbackException> { act(feedback, FeedbackAction.QUALIFY) }
        feedback = act(feedback, FeedbackAction.CLOSE)
        act(feedback, FeedbackAction.REOPEN)
        assertThat(ledger.creditsForParticipant(participantId)).hasSize(1)
        val entry = ledger.scoreHistoryPage(participantId, originalSeason, "credit", null, 25).single()
        assertThat(entry.displayName).isEqualTo("Qualifying feedback")
        assertThat(entry.sourceOccurredAt).isEqualTo(at)
        assertThat(entry.displayName).doesNotContain(feedback.title)
        val summary = ScoringService(ledger, AdminGroupMembers { emptySet() }, DataSourceTransactionManager(dataSource))
            .scoreSummaryForParticipant(participantId, originalSeason.toString(), admin = false) as ParticipantScoreSummary
        assertThat(summary.breakdown.feedback).isEqualTo(4)
    }

    @Test
    fun `self awards inactive awards and unsafe ticket links are rejected without writes`() {
        var feedback = act(submit(), FeedbackAction.CLAIM, submitter)
        assertThat(assertThrows<FeedbackException> { act(feedback, FeedbackAction.QUALIFY, submitter) }.failure)
            .isEqualTo(FeedbackFailure.FORBIDDEN)
        feedback = act(feedback, FeedbackAction.RELEASE, submitter)
        feedback = act(feedback, FeedbackAction.CLAIM)
        listOf("javascript:alert(1)", "https://user:password@example.org/ticket", "/relative").forEach { url ->
            assertThrows<FeedbackException> { act(feedback, FeedbackAction.EXTERNAL_TICKET, url = url) }
        }
        participants.leave(submitter)
        assertThrows<FeedbackException> { act(feedback, FeedbackAction.QUALIFY) }
        feedback = act(feedback, FeedbackAction.DECLINE, note = "Not eligible for credit")
        assertThat(feedback.scoreDecision).isEqualTo(FeedbackScoreDecision.NOT_QUALIFIED)
        assertThat(ledger.creditsForParticipant(participantId)).isEmpty()
    }

    @Test
    fun `history failure rolls back both the scoring decision and the credit`() {
        val feedback = act(submit(), FeedbackAction.CLAIM)
        jdbcTemplate.execute("""CREATE FUNCTION reject_feedback_history() RETURNS trigger LANGUAGE plpgsql AS
            'BEGIN RAISE EXCEPTION ''history unavailable''; END'""")
        jdbcTemplate.execute("CREATE TRIGGER reject_feedback_history BEFORE INSERT ON program_feedback_history FOR EACH ROW EXECUTE FUNCTION reject_feedback_history()")
        try {
            assertThrows<org.springframework.dao.DataAccessException> { act(feedback, FeedbackAction.QUALIFY) }
        } finally {
            jdbcTemplate.execute("DROP TRIGGER reject_feedback_history ON program_feedback_history")
            jdbcTemplate.execute("DROP FUNCTION reject_feedback_history()")
        }
        assertThat(service.detail(feedback.id).scoreDecision).isEqualTo(FeedbackScoreDecision.PENDING)
        assertThat(service.detail(feedback.id).version).isEqualTo(feedback.version)
        assertThat(ledger.creditsForParticipant(participantId)).isEmpty()
    }

    @Test
    fun `pagination filters and permanent deletion retain no linked subject records`() {
        repeat(52) { submit("Feedback $it") }
        val first = service.search(FeedbackSearch(), admin)
        val newer = SubmitFeedbackRequest(UUID.randomUUID(), "New feedback", "Arrived after the first page")
        tx.executeWithoutResult {
            store.submit(participantId, store.currentSeason(), newer, at.plusSeconds(1))
        }
        val second = service.search(FeedbackSearch(cursor = requireNotNull(first.nextCursor)), admin)
        assertThat(first.items).hasSize(50)
        assertThat(second.items).hasSize(2)
        assertThat(second.nextCursor).isNull()
        assertThat(first.items.map { it.id }.intersect(second.items.map { it.id }.toSet())).isEmpty()
        participants.enroll(admin, "A12345", admin, "Admin")
        val feedback = act(first.items.first(), FeedbackAction.CLAIM)
        tx.executeWithoutResult { service.markRead(feedback.id, admin) }
        val takenOver = act(feedback, FeedbackAction.CLAIM, "other-admin@nav.no")
        assertThat(takenOver.history.first().previousOwnerName).isEqualTo("Admin")
        act(takenOver, FeedbackAction.CLAIM)
        assertThat(service.search(FeedbackSearch(owner = "mine"), admin).items).hasSize(1)
        val adminId = requireNotNull(participants.findByNavNoEmail(admin)).id
        tx.executeWithoutResult { participants.permanentlyDelete(adminId) }
        assertThat(service.detail(feedback.id).ownerEmail).isNull()
        val anonymized = service.detail(feedback.id)
        assertThat(anonymized.history.filter { it.actorEmail == admin }).isEmpty()
        assertThat(anonymized.history[1].previousOwnerName).isNull()
        assertThat(anonymized.status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM program_feedback_reads WHERE reader_email = ?", Int::class.java, admin,
        )).isZero()
        tx.executeWithoutResult { participants.permanentlyDelete(participantId) }
        assertThat(service.search(FeedbackSearch(), admin).items).isEmpty()
        assertThat(store.history(feedback.id)).isEmpty()
    }
}
