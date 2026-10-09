package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.events.*
import navikt.appsec.securitychampionapp.app.scoring.*
import navikt.appsec.securitychampionapp.integrations.postgress.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mockingDetails
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventClaimIntegrationTest {
    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var tx: TransactionTemplate
    private lateinit var store: PostgresEventClaimStore
    private lateinit var participants: ProgramParticipantRepository
    private lateinit var ledger: PostgresScoringLedger
    private lateinit var service: EventClaimService
    private lateinit var host: UUID
    private lateinit var cohost: UUID
    private lateinit var season: UUID
    private val audit = mock<ProgramAuditService>()
    private val clock = Clock.fixed(Instant.parse("2026-10-07T14:00:00Z"), ZoneOffset.UTC)

    @BeforeAll
    fun setup() {
        dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
        }
        jdbc = JdbcTemplate(dataSource)
        tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
    }

    @AfterAll
    fun close() { dataSource.close() }

    @BeforeEach
    fun reset() {
        val flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load()
        flyway.clean()
        flyway.migrate()
        participants = ProgramParticipantRepository(jdbc)
        store = PostgresEventClaimStore(jdbc, ObjectMapper())
        ledger = PostgresScoringLedger(jdbc)
        service = EventClaimService(store, participants, ledger, ScoringService(ledger, audit), audit, clock)
        season = ledger.currentSeason().id
        jdbc.update("UPDATE program_seasons SET starts_on = '2026-01-01' WHERE id = ?", season)
        host = participant("host@nav.no")
        cohost = participant("cohost@nav.no")
    }

    private fun participant(email: String): UUID {
        participants.enroll(email, "T12345", email, email.substringBefore("@"))
        val id = requireNotNull(participants.findByNavNoEmail(email)).id
        jdbc.update("UPDATE program_participants SET created_at = '2026-01-01' WHERE id = ?", id)
        return id
    }

    private fun request() = EventClaimRequest(
        "Security workshop", "Delivered threat modeling exercises",
        Instant.parse("2026-09-30T10:00:00Z"), Instant.parse("2026-09-30T12:00:00Z"),
        "Oslo", "workshop", true, listOf("https://example.org/security-workshop"),
        "Invited the network in Slack on September 10",
        listOf(EventClaimContributorRequest(host, "Organized the exercises"), EventClaimContributorRequest(cohost, "Presented the threat model")),
    )

    private fun submit(request: EventClaimRequest = request()): EventClaim =
        requireNotNull(tx.execute { service.submit("host@nav.no", request) })

    private fun review(claim: EventClaim, participant: UUID, decision: ContributionStatus = ContributionStatus.APPROVED, actor: String = "admin@nav.no"): EventClaim =
        requireNotNull(tx.execute {
            service.review(claim.id, actor, EventClaimReviewRequest(participant, decision, "Verified invitation and delivery", claim.version))
        })

    @Test
    fun `approval publishes one event and awards full credit to each contributor`() {
        var claim = submit()
        assertThat(EventRepository(jdbc).getAllEvents()).isEmpty()
        assertThat(ledger.scoreForParticipant(host, season)).isZero()
        claim = review(claim, host)
        claim = review(claim, cohost)
        assertThat(ledger.scoreForParticipant(host, season)).isEqualTo(3)
        assertThat(ledger.scoreForParticipant(cohost, season)).isEqualTo(3)
        val event = EventRepository(jdbc).getAllEvents().single()
        assertThat(event.externalEvent).isTrue()
        assertThat(event.link).isEqualTo(request().links.first())
        assertThat(event.description).isEqualTo(request().description)
        assertThat(event.id).isEqualTo(claim.eventId.toString())
        assertThat(claim.reviews).hasSize(2)
        assertThat(claim.editable).isFalse()
        assertThrows<EventClaimException> { review(claim, cohost) }
        assertThat(ledger.creditsForParticipant(cohost)).hasSize(1)
        val history = ledger.scoreHistoryPage(cohost, season, "credit", null, 25).single()
        assertThat(history.displayName).isEqualTo(request().name)
        assertThat(history.sourceUrl).isEqualTo(request().links.first())
        assertThat(history.sourceOccurredAt).isEqualTo(request().startDate)
    }

    @Test
    fun `approval after reset credits the event season and revocation preserves that season`() {
        val submitted = submit()
        tx.executeWithoutResult { ledger.resetManually(LocalDate.parse("2026-10-01"), "New season", "admin@nav.no") }
        val approved = review(submitted, host)
        val newSeason = ledger.currentSeason().id
        assertThat(ledger.scoreForParticipant(host, season)).isEqualTo(3)
        assertThat(ledger.scoreForParticipant(host, newSeason)).isZero()
        review(approved, host, ContributionStatus.REVOKED)
        assertThat(ledger.scoreForParticipant(host, season)).isZero()
        assertThat(ledger.scoreForParticipant(host, newSeason)).isZero()
        assertThat(EventRepository(jdbc).getAllEvents()).hasSize(1)
    }

    @Test
    fun `revocation reverses rule repricing but preserves manual adjustments and stays revoked`() {
        val claim = review(submit(), host)
        fun reprice(points: Int) {
            tx.executeWithoutResult {
                val config = ledger.configuration()
                val request = ScoringConfigurationRequest(
                    config.version, config.tiers,
                    config.activities.map { if (it.creditType == ActivityCreditType.SECURITY_EVENT_CONTRIBUTION) it.copy(points = points) else it },
                    true, "Update contribution points",
                )
                val preview = ledger.previewConfiguration(request)
                ledger.saveConfiguration(request.copy(previewToken = preview.token), "admin@nav.no")
            }
        }
        reprice(5)
        tx.executeWithoutResult { ledger.addAdjustment(host, 2, "Manual correction", "admin@nav.no", claim.contributors.first { it.participantId == host }.creditId) }
        assertThat(ledger.scoreForParticipant(host, season)).isEqualTo(7)
        review(claim, host, ContributionStatus.REVOKED)
        assertThat(ledger.scoreForParticipant(host, season)).isEqualTo(2)
        reprice(8)
        assertThat(ledger.scoreForParticipant(host, season)).isEqualTo(2)
    }

    @Test
    fun `rejected claims can be revised without losing review history`() {
        var claim = submit()
        claim = review(claim, host, ContributionStatus.REJECTED)
        val revised = requireNotNull(tx.execute {
            service.submit("host@nav.no", request().copy(description = "Revised content", expectedVersion = claim.version), claim.id)
        })
        assertThat(revised.reviews).hasSize(1)
        assertThat(revised.contributors).allMatch { it.status == ContributionStatus.PENDING }
        assertThat(revised.description).isEqualTo("Revised content")
    }

    @Test
    fun `self approval stale edits and unrelated participant edits are denied`() {
        val claim = submit()
        assertThrows<EventClaimException> { review(claim, host, actor = "host@nav.no") }
        assertThrows<EventClaimException> { tx.execute { service.submit("cohost@nav.no", request().copy(expectedVersion = claim.version), claim.id) } }
        assertThrows<EventClaimException> { tx.execute { service.submit("host@nav.no", request().copy(expectedVersion = 0), claim.id) } }
        assertThat(ledger.creditsForParticipant(host)).isEmpty()
    }

    @Test
    fun `future old season pre enrollment and invalid links cannot be submitted`() {
        val initial = request()
        val invalid = listOf(
            initial.copy(endDate = clock.instant().plusSeconds(3600)),
            initial.copy(startDate = Instant.parse("2025-12-30T10:00:00Z")),
            initial.copy(links = listOf("javascript:alert(1)")),
            initial.copy(links = listOf("https://user:password@example.org")),
            initial.copy(invitationEvidence = ""),
            initial.copy(contributors = listOf(initial.contributors.first(), initial.contributors.first())),
        )
        invalid.forEach { assertThrows<EventClaimException> { submit(it) } }
        jdbc.update("UPDATE program_participants SET created_at = ? WHERE id = ?", Timestamp.from(clock.instant()), cohost)
        assertThrows<EventClaimException> { submit() }
        assertThat(store.list()).isEmpty()
    }

    @Test
    fun `duplicate event identity and shared links cannot produce separate claims`() {
        submit()
        assertThrows<EventClaimException> { submit() }
        assertThrows<EventClaimException> { submit(request().copy(name = "Different title")) }
        assertThat(store.list()).hasSize(1)
    }

    @Test
    fun `linking an existing event reuses its catalog entry`() {
        val id = UUID.randomUUID()
        val request = request()
        EventRepository(jdbc).addEvent(navikt.appsec.securitychampionapp.app.api.dto.Event(
            id.toString(), request.name, request.description, request.startDate.toString(), request.endDate.toString(),
            request.location, request.type, request.externalEvent, false,
        ))
        val claim = review(submit(request.copy(eventId = id)), host)
        assertThat(claim.eventId).isEqualTo(id)
        assertThat(EventRepository(jdbc).getAllEvents()).hasSize(1)
    }

    @Test
    fun `approval publication failure rolls back credit and review`() {
        val claim = submit()
        jdbc.execute("""CREATE FUNCTION reject_claim_publication() RETURNS trigger LANGUAGE plpgsql AS
            'BEGIN RAISE EXCEPTION ''publication unavailable''; END'""")
        jdbc.execute("CREATE TRIGGER reject_claim_publication BEFORE INSERT ON Events FOR EACH ROW EXECUTE FUNCTION reject_claim_publication()")
        assertThrows<org.springframework.dao.DataAccessException> { review(claim, host) }
        assertThat(ledger.creditsForParticipant(host)).isEmpty()
        assertThat(store.find(claim.id)?.reviews).isEmpty()
        assertThat(store.find(claim.id)?.published).isFalse()
    }

    @Test
    fun `only contributors see private claims and permanent deletion erases subject records`() {
        val outsider = participant("outsider@nav.no")
        val claim = review(submit(), host)
        assertThat(store.list(outsider)).isEmpty()
        assertThat(store.list(cohost)).hasSize(1)
        tx.executeWithoutResult { participants.permanentlyDelete(cohost) }
        assertThat(store.find(claim.id)?.contributors).hasSize(1)
        tx.executeWithoutResult { participants.permanentlyDelete(host) }
        assertThat(store.list()).isEmpty()
        assertThat(EventRepository(jdbc).getAllEvents()).hasSize(1)
    }

    @Test
    fun `maximum length claim link can be approved and published without truncation`() {
        val prefix = "https://example.org/"
        val link = prefix + "a".repeat(1000 - prefix.length)
        val claim = review(submit(request().copy(links = listOf(link))), host)
        assertThat(claim.published).isTrue()
        assertThat(EventRepository(jdbc).getAllEvents().single().link).isEqualTo(link)
        assertThat(ledger.scoreForParticipant(host, season)).isEqualTo(3)
        assertThrows<EventClaimException> { submit(request().copy(links = listOf(link + "a"))) }
    }

    @Test
    fun `claim loading uses three queries regardless of claim count and keeps histories separated`() {
        val observedJdbc = spy(jdbc)
        val observedStore = PostgresEventClaimStore(observedJdbc, ObjectMapper())
        val submitted = mutableListOf<EventClaim>()
        val outsider = participant("outsider@nav.no")
        for (index in 1..10) {
            val claim = submit(request().copy(
                name = "Security workshop $index",
                links = listOf("https://example.org/event-$index"),
            ))
            submitted += review(claim, cohost, ContributionStatus.REJECTED)
            if (index in setOf(1, 5, 10)) {
                for (participantId in listOf(null, host, cohost)) {
                    clearInvocations(observedJdbc)
                    val loaded = observedStore.list(participantId)
                    val queryCount = mockingDetails(observedJdbc).invocations.count {
                        it.method.name == "query" && it.method.parameterTypes.getOrNull(1) == RowMapper::class.java
                    }
                    assertThat(queryCount).isEqualTo(3)
                    assertThat(loaded.map { it.id }).containsExactlyElementsOf(submitted.asReversed().map { it.id })
                    assertThat(loaded).hasSize(index)
                    assertThat(loaded.first().id).isEqualTo(claim.id)
                    loaded.forEach { hydrated ->
                        assertThat(hydrated).isEqualTo(submitted.single { it.id == hydrated.id })
                        assertThat(hydrated.contributors).hasSize(2)
                        assertThat(hydrated.reviews.single().participantId).isEqualTo(cohost)
                    }
                }
            }
        }
        assertThat(observedStore.list(outsider)).isEmpty()
    }

    @Test
    fun `removed inactive contributors remain named in retained reviews until permanent deletion`() {
        val rejected = review(submit(), cohost, ContributionStatus.REJECTED)
        val revised = requireNotNull(tx.execute {
            service.submit("host@nav.no", request().copy(
                contributors = listOf(request().contributors.first()), expectedVersion = rejected.version,
            ), rejected.id)
        })
        tx.executeWithoutResult { participants.updateStatus(cohost, false, "admin@nav.no") }
        val overview = service.overview("host@nav.no", false)
        assertThat(overview.participants).noneMatch { it.id == cohost }
        val claim = overview.claims.single()
        assertThat(claim.contributors).hasSize(1)
        assertThat(claim.reviews.single().fullName).isEqualTo("cohost")
        assertThat(store.find(revised.id)?.reviews?.single()?.fullName).isEqualTo("cohost")
        tx.executeWithoutResult { participants.permanentlyDelete(cohost) }
        assertThat(store.find(revised.id)?.reviews).isEmpty()
    }

    @Test
    fun `review history has a matching lookup and ordering index`() {
        val definition = jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'event_claim_reviews' AND indexname = 'event_claim_reviews_claim_created_idx'",
            String::class.java,
        )
        assertThat(definition).contains("(claim_id, created_at, id)")
    }
}
