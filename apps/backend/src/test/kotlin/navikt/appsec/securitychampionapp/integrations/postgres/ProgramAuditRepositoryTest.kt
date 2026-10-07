package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramAuditRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresScoringLedger
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgramAuditRepositoryTest {
    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var repository: ProgramAuditRepository
    private lateinit var auditService: ProgramAuditService
    private lateinit var scoringRepository: PostgresScoringLedger
    private lateinit var participantRepository: ProgramParticipantRepository
    private lateinit var flyway: Flyway

    @BeforeAll
    fun setupRepository() {
        dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 2
        }
        jdbcTemplate = JdbcTemplate(dataSource)
        repository = ProgramAuditRepository(jdbcTemplate, JsonMapper.builder().build())
        auditService = ProgramAuditService(repository, Clock.systemUTC())
        scoringRepository = PostgresScoringLedger(jdbcTemplate)
        participantRepository = ProgramParticipantRepository(jdbcTemplate)
        flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
    }

    @AfterAll
    fun closeDataSource() {
        dataSource.close()
    }

    @BeforeEach
    fun resetDatabase() {
        flyway.clean()
        flyway.migrate()
    }

    @Test
    fun `should search and paginate admin audit events`() {
        auditService.record(
            action = "SLACK_SCORING_SYNC_COMPLETED",
            outcome = AuditOutcome.SUCCEEDED,
            actorNavNoEmail = "admin@nav.no",
            details = mapOf("creditsAwarded" to 4),
        )
        auditService.record(
            action = "ADMIN_MUTATION",
            outcome = AuditOutcome.FAILED,
            actorNavNoEmail = "other@nav.no",
            details = mapOf("route" to "/api/admin/participants/{id}/status"),
        )

        val (page, total) = repository.adminPage("creditsAwarded", page = 0, size = 1)

        assertThat(total).isEqualTo(1)
        assertThat(page).hasSize(1)
        assertThat(page.single().action).isEqualTo("SLACK_SCORING_SYNC_COMPLETED")
        assertThat(page.single().details).containsEntry("creditsAwarded", "4")
    }

    @Test
    fun `should keep successful participant history until target deletion and retain a targetless tombstone`() {
        val participantId = createParticipant("person@nav.no")
        auditService.recordParticipantEvent(participantId, "PARTICIPANT_ENROLLED")
        auditService.record("ADMIN_MUTATION", AuditOutcome.FAILED, "admin@nav.no", participantId)

        assertThat(auditService.recordParticipantDeletion()).isTrue()
        jdbcTemplate.update("DELETE FROM program_participants WHERE id = ?", participantId)

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM program_audit_events WHERE target_participant_id = ?",
                Int::class.java,
                participantId,
            )
        ).isZero()
        val tombstone = jdbcTemplate.queryForMap(
                """
                    SELECT action, actor_nav_no_email, target_participant_id, correlation_id, details
                    FROM program_audit_events
                """.trimIndent()
        )
        assertThat(tombstone["action"]).isEqualTo("PARTICIPANT_PERMANENTLY_DELETED")
        assertThat(tombstone["actor_nav_no_email"]).isNull()
        assertThat(tombstone["target_participant_id"]).isNull()
        assertThat(tombstone["correlation_id"]).isNull()
        assertThat(tombstone["details"].toString()).isEqualTo("{}")
    }

    @Test
    fun `should expose inactive participant history without actor or failed operations`() {
        val participantId = createParticipant("person@nav.no")
        val correlationId = UUID.randomUUID()
        scoringRepository.awardCredit(
            participantId,
            ActivityCreditType.GITHUB_COMMIT,
            "commit-42",
            "commit:42",
            correlationId,
        )
        auditService.recordRun(
            "SLACK_SCORING_SYNC_COMPLETED",
            AuditOutcome.SUCCEEDED,
            AuditRunContext(correlationId, "requester@nav.no"),
            mapOf("creditsAwarded" to 1),
        )
        scoringRepository.addAdjustment(participantId, -1, "Correct an over-award", "admin@nav.no", null)
        participantRepository.updateStatus(participantId, active = false, actorNavNoEmail = "admin@nav.no")
        auditService.recordParticipantEvent(participantId, "PARTICIPANT_LEFT")
        auditService.recordParticipantEvent(participantId, "PARTICIPANT_REJOINED", outcome = AuditOutcome.FAILED)

        val history = repository.participantHistory(participantId)

        assertThat(history.map { it.type }).contains("MEMBERSHIP", "CREDIT", "ADJUSTMENT")
        assertThat(history.map { it.action }).doesNotContain("PARTICIPANT_REJOINED")
        assertThat(history.single { it.type == "CREDIT" }.sourceReference).isEqualTo("commit:42")
        assertThat(history.single { it.type == "ADJUSTMENT" }.reason).isEqualTo("Correct an over-award")
        assertThat(history.all { it.javaClass.declaredFields.none { field -> field.name.contains("actor") } }).isTrue()
        val syncEvent = repository.adminPage("requester@nav.no", 0, 10).first.single()
        assertThat(syncEvent.actorNavNoEmail).isEqualTo("requester@nav.no")
        assertThat(syncEvent.correlationId).isEqualTo(correlationId)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT audit_correlation_id FROM activity_credits WHERE participant_id = ?",
                UUID::class.java,
                participantId,
            )
        ).isEqualTo(correlationId)
    }

    @Test
    fun `should purge failed membership attempts and operational events older than twelve months only`() {
        val participantId = createParticipant("person@nav.no")
        auditService.record("ADMIN_MUTATION", AuditOutcome.SUCCEEDED)
        auditService.recordParticipantEvent(participantId, "PARTICIPANT_LEFT")
        auditService.recordParticipantEvent(participantId, "PARTICIPANT_REJOINED", outcome = AuditOutcome.FAILED)
        jdbcTemplate.update("UPDATE program_audit_events SET created_at = NOW() - INTERVAL '13 months'")

        assertThat(repository.deleteExpiredOperationalEvents(Instant.now())).isEqualTo(2)

        val retained = repository.adminPage(null, 0, 10).first
        assertThat(retained.map { it.action }).containsExactly("PARTICIPANT_LEFT")
    }

    @Test
    fun `should expire legacy operations but retain participant status credits and adjustment history`() {
        val participantId = createParticipant("person@nav.no")
        participantRepository.updateStatus(participantId, false, "admin@nav.no")
        jdbcTemplate.update(
            """
                INSERT INTO program_participant_audit (
                    participant_id, action, before_values, after_values, created_at
                ) VALUES (?, 'SLACK_MAPPING_ADDED', '{}', '{}', NOW() - INTERVAL '13 months')
            """.trimIndent(), participantId,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (action, before_values, after_values, created_at)
                VALUES ('SEASON_RESET_DATE_UPDATED', '{}', '{}', NOW() - INTERVAL '13 months')
            """.trimIndent(),
        )
        jdbcTemplate.update("UPDATE program_participant_audit SET created_at = NOW() - INTERVAL '13 months'")
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (action, before_values, after_values)
                VALUES ('DELTA_CATEGORY_ADDED', '{}', '{}')
            """.trimIndent(),
        )
        jdbcTemplate.update("UPDATE program_participants SET status = 'ACTIVE' WHERE id = ?", participantId)
        scoringRepository.awardCredit(participantId, ActivityCreditType.GITHUB_COMMIT, "commit:1", "Commit 1")
        scoringRepository.addAdjustment(participantId, 1, "Correction", "admin@nav.no", null)
        jdbcTemplate.update("UPDATE program_scoring_audit SET created_at = NOW() - INTERVAL '13 months' "
            + "WHERE action = 'POINTS_ADJUSTED'")
        jdbcTemplate.update("UPDATE activity_credits SET awarded_at = NOW() - INTERVAL '13 months'")
        jdbcTemplate.update("UPDATE point_adjustments SET created_at = NOW() - INTERVAL '13 months'")

        assertThat(repository.deleteExpiredOperationalEvents(Instant.now())).isEqualTo(3)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM program_participant_audit", Int::class.java))
            .isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM program_scoring_audit", Int::class.java))
            .isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_credits", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_adjustments", Int::class.java)).isEqualTo(1)
    }

    @Test
    fun `should not expose pre-rollout domain records as new participant history`() {
        val participantId = createParticipant("person@nav.no")
        scoringRepository.awardCredit(
            participantId,
            ActivityCreditType.GITHUB_COMMIT,
            "commit-legacy",
            "commit:legacy",
        )
        scoringRepository.addAdjustment(participantId, 1, "Legacy correction", "admin@nav.no", null)
        participantRepository.updateStatus(participantId, active = false, actorNavNoEmail = "admin@nav.no")
        jdbcTemplate.update("UPDATE activity_credits SET awarded_at = NOW() - INTERVAL '1 day'")
        jdbcTemplate.update("UPDATE point_adjustments SET created_at = NOW() - INTERVAL '1 day'")
        jdbcTemplate.update("UPDATE program_participant_audit SET created_at = NOW() - INTERVAL '1 day'")
        auditService.recordParticipantEvent(participantId, "PARTICIPANT_LEFT", details = mapOf("status" to "LEFT"))

        assertThat(repository.participantHistory(participantId).map { it.action })
            .containsExactly("PARTICIPANT_LEFT")
    }

    @Test
    fun `should capture only after commit and isolate an unavailable audit table from scoring`() {
        val manager = DataSourceTransactionManager(dataSource)
        val proxyFactory = ProxyFactory(repository).apply {
            isProxyTargetClass = true
            addAdvice(TransactionInterceptor().apply {
                transactionManager = manager
                transactionAttributeSource = AnnotationTransactionAttributeSource()
            })
        }
        val transactionalRepository = proxyFactory.proxy as ProgramAuditRepository
        val transactionalService = ProgramAuditService(transactionalRepository, Clock.systemUTC())
        val participantId = createParticipant("person@nav.no")
        val transaction = TransactionTemplate(manager)

        transaction.executeWithoutResult { status ->
            transactionalService.recordParticipantEvent(participantId, "PARTICIPANT_ENROLLED")
            assertThat(repository.adminPage(null, 0, 10).second).isZero()
            status.setRollbackOnly()
        }
        assertThat(repository.adminPage(null, 0, 10).second).isZero()

        transaction.executeWithoutResult {
            scoringRepository.awardCredit(
                participantId,
                ActivityCreditType.GITHUB_COMMIT,
                "commit-success",
                "commit:success",
            )
            transactionalService.recordParticipantEvent(participantId, "PARTICIPANT_ENROLLED")
        }
        assertThat(repository.adminPage(null, 0, 10).second).isEqualTo(1)

        jdbcTemplate.execute("ALTER TABLE program_audit_events RENAME TO unavailable_audit_events")
        try {
            transaction.executeWithoutResult {
                scoringRepository.addAdjustment(participantId, 1, "Correction", "admin@nav.no", null)
                transactionalService.record("POINTS_ADJUSTED", AuditOutcome.SUCCEEDED, "admin@nav.no", participantId)
            }
        } finally {
            jdbcTemplate.execute("ALTER TABLE unavailable_audit_events RENAME TO program_audit_events")
        }
        assertThat(scoringRepository.scoreForParticipant(participantId, scoringRepository.currentSeason().id))
            .isEqualTo(2)
    }

    @Test
    fun `should not reintroduce a deleted requester identity in an async outcome`() {
        val requesterId = createParticipant("requester@nav.no")
        val run = auditService.captureRunContext("requester@nav.no")
        jdbcTemplate.update("DELETE FROM program_participants WHERE id = ?", requesterId)

        auditService.recordRun("SLACK_SCORING_SYNC_COMPLETED", AuditOutcome.SUCCEEDED, run)

        val entry = repository.adminPage(null, 0, 10).first.single()
        assertThat(entry.actorNavNoEmail).isNull()
        assertThat(entry.details).isEmpty()
    }

    @Test
    fun `should omit the deletion administrator when they delete themselves`() {
        val targetId = createParticipant("self@nav.no")
        val actor = auditService.captureRunContext("self@nav.no")
        jdbcTemplate.update("DELETE FROM program_participants WHERE id = ?", targetId)

        assertThat(auditService.recordParticipantDeletion(actor, targetId)).isTrue()

        val entry = repository.adminPage(null, 0, 10).first.single()
        assertThat(entry.actorNavNoEmail).isNull()
        assertThat(entry.targetParticipantId).isNull()
        assertThat(entry.correlationId).isNull()
        assertThat(entry.details).isEmpty()
    }

    @Test
    fun `should retain an unrelated enrolled deletion administrator without retaining target data`() {
        createParticipant("administrator@nav.no")
        val targetId = createParticipant("target@nav.no")
        val actor = auditService.captureRunContext("administrator@nav.no")
        jdbcTemplate.update("DELETE FROM program_participants WHERE id = ?", targetId)

        assertThat(auditService.recordParticipantDeletion(actor, targetId)).isTrue()

        val entry = repository.adminPage(null, 0, 10).first.single()
        assertThat(entry.actorNavNoEmail).isEqualTo("administrator@nav.no")
        assertThat(entry.targetParticipantId).isNull()
        assertThat(entry.correlationId).isNull()
        assertThat(entry.details).isEmpty()
        assertThat(entry.action).isEqualTo("PARTICIPANT_PERMANENTLY_DELETED")
        assertThat(entry.outcome).isEqualTo(AuditOutcome.SUCCEEDED)
    }

    @Test
    fun `should retain a nonparticipant deletion administrator without retaining target data`() {
        val targetId = createParticipant("target@nav.no")
        val actor = auditService.captureRunContext("nonparticipant-admin@nav.no")
        jdbcTemplate.update("DELETE FROM program_participants WHERE id = ?", targetId)

        assertThat(auditService.recordParticipantDeletion(actor, targetId)).isTrue()

        val entry = repository.adminPage(null, 0, 10).first.single()
        assertThat(entry.actorNavNoEmail).isEqualTo("nonparticipant-admin@nav.no")
        assertThat(entry.targetParticipantId).isNull()
        assertThat(entry.correlationId).isNull()
        assertThat(entry.details).isEmpty()
    }

    @Test
    fun `should omit a deletion administrator whose participant record disappears during the operation`() {
        val actorId = createParticipant("administrator@nav.no")
        val targetId = createParticipant("target@nav.no")
        val actor = auditService.captureRunContext("administrator@nav.no")
        jdbcTemplate.update("DELETE FROM program_participants WHERE id IN (?, ?)", actorId, targetId)

        assertThat(auditService.recordParticipantDeletion(actor, targetId)).isTrue()

        assertThat(repository.adminPage(null, 0, 10).first.single().actorNavNoEmail).isNull()
    }

    private fun createParticipant(navNoEmail: String): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
                INSERT INTO program_participants (id, nav_no_email, nav_ident, email, fullname)
                VALUES (?, ?, 'A12345', ?, 'Person')
            """.trimIndent(),
            id,
            navNoEmail,
            navNoEmail,
        )
        return id
    }
}
