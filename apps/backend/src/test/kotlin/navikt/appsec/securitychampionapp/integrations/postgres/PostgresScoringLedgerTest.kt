package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.jobs.ScoringJobLockKeys
import navikt.appsec.securitychampionapp.app.scoring.DeltaScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.INTERRUPTED_SYNC_SUMMARY
import navikt.appsec.securitychampionapp.app.scoring.SlackScoringStatusService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.CreditAwardResult
import navikt.appsec.securitychampionapp.app.scoring.DeltaSyncSummary
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMappingHasCreditsException
import navikt.appsec.securitychampionapp.app.scoring.SlackSyncSummary
import navikt.appsec.securitychampionapp.integrations.delta.DeltaFailure
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.app.scoring.RecognitionEntry
import navikt.appsec.securitychampionapp.integrations.postgress.AdminDashboardRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEligibleCategoryRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEventMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaSyncOutcome
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresScoringLedger
import navikt.appsec.securitychampionapp.integrations.postgress.SlackScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.SlackSyncOutcome
import navikt.appsec.securitychampionapp.integrations.postgress.SlackIdentityMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.GitHubIdentityMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.GitHubScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.app.scoring.GitHubScoringService
import navikt.appsec.securitychampionapp.app.scoring.GitHubScoringStatusService
import navikt.appsec.securitychampionapp.app.scoring.GitHubSyncSummary
import navikt.appsec.securitychampionapp.integrations.github.GitHubContribution
import navikt.appsec.securitychampionapp.integrations.github.GitHubContributionSource
import navikt.appsec.securitychampionapp.integrations.github.GitHubIdentity
import navikt.appsec.securitychampionapp.integrations.github.GitHubIntegrationException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.dao.DuplicateKeyException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresScoringLedgerTest {
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
    private lateinit var repository: PostgresScoringLedger
    private lateinit var adminDashboardRepository: AdminDashboardRepository
    private lateinit var slackScoringStatusRepository: SlackScoringStatusRepository
    private lateinit var slackIdentityMappingRepository: SlackIdentityMappingRepository
    private lateinit var deltaEventMappingRepository: DeltaEventMappingRepository
    private lateinit var deltaEligibleCategoryRepository: DeltaEligibleCategoryRepository
    private lateinit var deltaScoringStatusRepository: DeltaScoringStatusRepository
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
        repository = PostgresScoringLedger(jdbcTemplate)
        adminDashboardRepository = AdminDashboardRepository(jdbcTemplate)
        slackScoringStatusRepository = SlackScoringStatusRepository(jdbcTemplate)
        slackIdentityMappingRepository = SlackIdentityMappingRepository(jdbcTemplate)
        deltaEventMappingRepository = DeltaEventMappingRepository(jdbcTemplate)
        deltaEligibleCategoryRepository = DeltaEligibleCategoryRepository(jdbcTemplate)
        deltaScoringStatusRepository = DeltaScoringStatusRepository(jdbcTemplate)
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
    fun `should persist GitHub credits once and remove mappings source facts and history on deletion`() {
        val id = createParticipant("github@nav.no")
        val now = Instant.now()
        jdbcTemplate.update(
            "UPDATE program_participants SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.from(now.minusSeconds(3600)), id,
        )
        val mapping = GitHubIdentityMappingRepository(jdbcTemplate)
        val originalContributions = listOf(
            GitHubContribution(10, ActivityCreditType.GITHUB_PULL_REQUEST, "navikt/security-playbook:pr:1", now),
            GitHubContribution(10, ActivityCreditType.GITHUB_COMMIT, "navikt/security-playbook:commit:a", now),
        )
        var contributions = originalContributions
        val source = object : GitHubContributionSource {
            override fun identities() = listOf(GitHubIdentity(10, "person", "github@nav.no"))
            override fun contributions(since: Instant, until: Instant) = contributions
        }
        val service = GitHubScoringService(source, mapping, repository, ScoringService(repository))
        assertThat(service.sync(now)).isEqualTo(GitHubSyncSummary(2, 2, 0, 0))
        assertThat(service.sync(now)).isEqualTo(GitHubSyncSummary(2, 0, 2, 0))
        contributions = emptyList()
        assertThat(service.sync(now).creditsAwarded).isZero()
        assertThat(repository.scoreForParticipant(id, repository.currentSeason().id)).isEqualTo(4)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_credits WHERE activity_at IS NOT NULL", Int::class.java,
            ),
        ).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM program_participant_audit WHERE participant_id = ?", Int::class.java, id,
            ),
        ).isEqualTo(1)
        assertThat(ProgramParticipantRepository(jdbcTemplate).permanentlyDelete(id).isOk).isTrue()
        listOf("github_account_mappings", "activity_credits", "program_participant_audit").forEach { table ->
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)).isZero()
        }
        contributions = originalContributions
        assertThat(service.sync(now).unmappedAuthors).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM program_participants", Int::class.java)).isZero()
        val reenrolledId = createParticipant("github@nav.no")
        assertThat(service.sync(Instant.now()).creditsAwarded).isZero()
        assertThat(repository.scoreForParticipant(reenrolledId, repository.currentSeason().id)).isZero()
    }

    @Test
    fun `should match canonical email reverify identity and reject ambiguous mappings`() {
        val id = createParticipant("canonical@nav.no")
        jdbcTemplate.update("UPDATE program_participants SET email = 'profile@nav.no' WHERE id = ?", id)
        val mapping = GitHubIdentityMappingRepository(jdbcTemplate)
        val now = Instant.now()
        assertThat(mapping.refresh(listOf(GitHubIdentity(10, "person", "CANONICAL@nav.no")), now)[10]?.participantId)
            .isEqualTo(id)
        assertThatThrownBy {
            mapping.refresh(
                listOf(GitHubIdentity(10, "person", "canonical@nav.no"), GitHubIdentity(11, "other", "canonical@nav.no")),
                now,
            )
        }.isInstanceOf(GitHubIntegrationException::class.java)
        assertThat(mapping.refresh(listOf(GitHubIdentity(10, "person", "profile@nav.no")), now)).isEmpty()
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM github_account_mappings", Int::class.java)).isZero()
    }

    @Test
    fun `should enforce GitHub uniqueness across participants and reject stale season or enrollment evidence`() {
        val id = createParticipant("one@nav.no")
        val other = createParticipant("two@nav.no")
        val now = Instant.now()
        val season = repository.currentSeason()
        jdbcTemplate.update(
            "UPDATE program_participants SET created_at = ? WHERE id IN (?, ?)",
            java.sql.Timestamp.from(now.minusSeconds(60)), id, other,
        )
        fun award(participant: UUID, at: Instant = now, seasonId: UUID = season.id) =
            repository.awardGitHubCredit(
                participant, ActivityCreditType.GITHUB_PULL_REQUEST, "pr:unique", "pr:unique", null, at, seasonId,
            )
        assertThat(award(id, now.minusSeconds(61))).isEqualTo(CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING)
        assertThatThrownBy { award(id, seasonId = UUID.randomUUID()) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(award(id)).isEqualTo(CreditAwardResult.AWARDED)
        assertThat(award(other)).isEqualTo(CreditAwardResult.DUPLICATE)
        assertThat(repository.scoreForParticipant(other, season.id)).isZero()
    }

    @Test
    fun `should preserve GitHub last success on failure and identify interrupted runs`() {
        val status = GitHubScoringStatusRepository(jdbcTemplate)
        val now = Instant.parse("2026-10-06T12:00:00.123456Z")
        status.recordStarted(now)
        status.recordSucceeded(now, GitHubSyncSummary(5, 2, 2, 1))
        status.recordStarted(now.plusSeconds(60))
        status.recordFailed("GitHub organization SAML identities are unavailable")
        assertThat(status.find(true).lastSuccessAt).isEqualTo(now)
        assertThat(status.find(true).outcome).isEqualTo("FAILED")
        status.recordStarted(now.plusSeconds(120))
        val service = GitHubScoringStatusService(status, PostgresJobLock(dataSource), true)
        assertThat(service.status().outcome).isEqualTo("FAILED")
        assertThat(service.status().failureSummary).isEqualTo(INTERRUPTED_SYNC_SUMMARY)
    }

    @Test
    fun `should award fixed points once per participant activity key`() {
        val participantId = createParticipant("person@nav.no")

        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.GITHUB_PULL_REQUEST,
                "pr:42",
                "Pull request 42",
            )
        ).isEqualTo(CreditAwardResult.AWARDED)
        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.GITHUB_PULL_REQUEST,
                "pr:42",
                "Pull request 42",
            )
        ).isEqualTo(CreditAwardResult.DUPLICATE)

        val overview = ScoringService(repository).adminOverview()
        assertThat(overview.participants.single().points).isEqualTo(3)
        assertThat(repository.creditsForParticipant(participantId).single().points).isEqualTo(3)
    }

    @Test
    fun `should award Slack participation only once for a participant week`() {
        val participantId = createParticipant("person@nav.no")
        val first = repository.awardCredit(
            participantId,
            ActivityCreditType.SLACK_WEEK,
            "2026-10-05",
            "CN8N938K1:1791187260.000001",
        )
        val overlappingSync = repository.awardCredit(
            participantId,
            ActivityCreditType.SLACK_WEEK,
            "2026-10-05",
            "CN8N938K1:1791187320.000001",
        )

        assertThat(first).isEqualTo(CreditAwardResult.AWARDED)
        assertThat(overlappingSync).isEqualTo(CreditAwardResult.DUPLICATE)
        val credits = repository.creditsForParticipant(participantId)
        assertThat(credits).hasSize(1)
        assertThat(credits.single().points).isEqualTo(1)
    }

    @Test
    fun `should retain Slack credit after deactivation and allow linked correction`() {
        val participantId = createParticipant("person@nav.no")
        repository.awardCredit(
            participantId,
            ActivityCreditType.SLACK_WEEK,
            "2026-10-05",
            "CN8N938K1:1791187260.000001",
        )
        val credit = repository.creditsForParticipant(participantId).single()
        jdbcTemplate.update(
            "UPDATE program_participants SET status = 'DEACTIVATED' WHERE id = ?",
            participantId,
        )

        assertThat(repository.creditsForParticipant(participantId)).containsExactly(credit)
        val adjustment = repository.addAdjustment(
            participantId,
            -1,
            "Correct a Slack credit",
            "admin@nav.no",
            credit.id,
        )

        assertThat(adjustment.scoreBefore).isEqualTo(1)
        assertThat(adjustment.scoreAfter).isZero()
        assertThat(repository.creditsForParticipant(participantId)).containsExactly(credit)
    }

    @Test
    fun `should assign an adjustment to the source credit season`() {
        val participantId = createParticipant("person@nav.no")
        val today = LocalDate.now(ZoneId.of("Europe/Oslo"))
        val firstSeason = repository.currentSeason()
        repository.awardCredit(participantId, ActivityCreditType.DELTA_REGISTRATION, "event:1", "Event 1")

        repository.resetManually(today.plusDays(1), "Start next season", "admin@nav.no")
        repository.resetManually(today.plusDays(2), "Start another season", "admin@nav.no")

        val creditId = repository.creditsForParticipant(participantId).single().id
        val adjustment = repository.addAdjustment(
            participantId,
            -1,
            "Correct duplicate registration",
            "admin@nav.no",
            creditId,
        )

        assertThat(adjustment.seasonId).isEqualTo(firstSeason.id)
        assertThat(adjustment.scoreBefore).isEqualTo(1)
        assertThat(adjustment.scoreAfter).isZero()
        assertThat(repository.scoreForParticipant(participantId, firstSeason.id)).isZero()
        assertThat(repository.scoreForParticipant(participantId, repository.currentSeason().id)).isZero()
    }

    @Test
    fun `should preserve season history and advance the scheduled reset`() {
        val participantId = createParticipant("person@nav.no")
        val initialSeason = repository.currentSeason()
        repository.awardCredit(participantId, ActivityCreditType.SLACK_WEEK, "2026-W40", "2026-W40")

        assertThat(repository.resetIfDue(initialSeason.nextResetDate)).isTrue()

        val nextSeason = repository.currentSeason()
        assertThat(nextSeason.startsOn).isEqualTo(initialSeason.nextResetDate)
        assertThat(nextSeason.nextResetDate).isEqualTo(LocalDate.of(initialSeason.nextResetDate.year + 1, 1, 1))
        assertThat(repository.scoreForParticipant(participantId, initialSeason.id)).isEqualTo(1)
        assertThat(repository.scoreForParticipant(participantId, nextSeason.id)).isZero()
    }

    @Test
    fun `should rank ties together and omit zero score participants`() {
        val service = ScoringService(repository)
        val participantIds = (1..6).map { createParticipant("person$it@nav.no") }
        val zeroScoreParticipant = createParticipant("zero@nav.no")
        participantIds.forEachIndexed { index, id ->
            repository.awardCredit(id, ActivityCreditType.SLACK_WEEK, "week:$index", "week $index")
        }

        val recognition = service.recognition()
        val leaderboard = service.leaderboard()

        assertThat(recognition).hasSize(6).allSatisfy { assertThat(it.rank).isEqualTo(1) }
        assertThat(leaderboard).hasSize(6).allSatisfy {
            assertThat(it.rank).isEqualTo(1)
            assertThat(it.points).isEqualTo(1)
        }
        assertThat(zeroScoreParticipant).isNotNull()
        assertThat(recognition.map { it.fullName }).doesNotContain("zero")
    }

    @Test
    fun `should include all ties at fifth in recognition and exclude lower ranks and zero scores`() {
        val scores = listOf(
            "first" to 7,
            "second" to 6,
            "third" to 5,
            "fourth" to 4,
            "zebra" to 3,
            "amber" to 3,
            "below" to 2,
            "zero" to 0,
        )
        scores.forEach { (name, points) ->
            val participantId = createParticipant("$name@nav.no")
            repeat(points) { week ->
                assertThat(
                    repository.awardCredit(
                        participantId,
                        ActivityCreditType.SLACK_WEEK,
                        "week:$week",
                        "Week $week",
                    )
                ).isEqualTo(CreditAwardResult.AWARDED)
            }
        }
        val service = ScoringService(repository)

        assertThat(service.recognition()).containsExactly(
            RecognitionEntry("first", 1),
            RecognitionEntry("second", 2),
            RecognitionEntry("third", 3),
            RecognitionEntry("fourth", 4),
            RecognitionEntry("amber", 5),
            RecognitionEntry("zebra", 5),
        )
        assertThat(service.leaderboard().map { it.fullName to it.rank }).containsExactly(
            "first" to 1,
            "second" to 2,
            "third" to 3,
            "fourth" to 4,
            "amber" to 5,
            "zebra" to 5,
            "below" to 7,
        )
    }

    @Test
    fun `should reject awards for inactive participants without creating credits`() {
        val participantId = createParticipant("person@nav.no")
        jdbcTemplate.update(
            "UPDATE program_participants SET status = 'DEACTIVATED' WHERE id = ?",
            participantId,
        )

        val result = repository.awardCredit(
            participantId,
            ActivityCreditType.SLACK_WEEK,
            "week:1",
            "week 1",
        )

        assertThat(result).isEqualTo(CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING)
        assertThat(repository.creditsForParticipant(participantId)).isEmpty()
    }

    @Test
    fun `should require unique approved Slack mappings and audit changes`() {
        val participantId = createParticipant("person@nav.no")
        slackIdentityMappingRepository.recordUnmappedAuthor("U_SLACK")

        assertThat(slackIdentityMappingRepository.addMapping("U_SLACK", participantId, "admin@nav.no")).isTrue()
        val mapping = slackIdentityMappingRepository.mappingOverview().first.single()
        assertThat(mapping.slackUserId).isEqualTo("U_SLACK")
        assertThat(mapping.participantId).isEqualTo(participantId)
        assertThat(slackIdentityMappingRepository.mappingOverview().second).isEmpty()
        assertThatThrownBy {
            slackIdentityMappingRepository.addMapping("U_SECOND", participantId, "admin@nav.no")
        }.isInstanceOf(DuplicateKeyException::class.java)

        assertThat(slackIdentityMappingRepository.removeMapping("U_SLACK", "admin@nav.no")).isTrue()
        assertThat(slackIdentityMappingRepository.mappingOverview().first).isEmpty()
        assertThat(slackIdentityMappingRepository.mappingOverview().second.map { it.slackUserId })
            .containsExactly("U_SLACK")
        assertThat(
            jdbcTemplate.queryForList(
                "SELECT action FROM program_participant_audit ORDER BY id",
                String::class.java,
            )
        ).containsExactly("SLACK_ACCOUNT_MAPPED", "SLACK_ACCOUNT_UNMAPPED")
    }

    @Test
    fun `should map a Slack author to the participant with a matching nav no email`() {
        val participantId = createParticipant("person@nav.no")
        slackIdentityMappingRepository.recordUnmappedAuthor("U_SLACK")

        assertThat(
            slackIdentityMappingRepository.addMappingByNavNoEmail("U_SLACK", "Person@NAV.no", "system:test")
        ).isTrue()
        assertThat(
            slackIdentityMappingRepository.addMappingByNavNoEmail("U_OTHER", "person@nav.no", "system:test")
        ).isFalse()
        assertThat(
            slackIdentityMappingRepository.addMappingByNavNoEmail("U_UNKNOWN", "unknown@nav.no", "system:test")
        ).isFalse()

        assertThat(slackIdentityMappingRepository.mappedParticipants()["U_SLACK"]?.participantId)
            .isEqualTo(participantId)
        assertThat(slackIdentityMappingRepository.mappingOverview().second).isEmpty()
        assertThat(
            jdbcTemplate.queryForList("SELECT actor_nav_no_email FROM program_participant_audit", String::class.java)
        ).containsExactly("system:test")
    }

    @Test
    fun `should create and remove an explicitly named Delta event mapping with audit history`() {
        val mappingId = UUID.randomUUID()
        val deltaEventUuid = UUID.randomUUID()

        val created = deltaEventMappingRepository.addMapping(
            mappingId,
            "Security Champion meetup",
            deltaEventUuid,
            "admin@nav.no",
        )

        assertThat(created.id).isEqualTo(mappingId)
        assertThat(created.programEventName).isEqualTo("Security Champion meetup")
        assertThat(created.deltaEventUuid).isEqualTo(deltaEventUuid)
        assertThat(deltaEventMappingRepository.findAll()).containsExactly(created)
        assertThat(deltaEventMappingRepository.removeMapping(mappingId, "admin@nav.no")).isTrue()
        assertThat(deltaEventMappingRepository.findAll()).isEmpty()
        assertThat(
            jdbcTemplate.queryForList(
                "SELECT action FROM program_scoring_audit ORDER BY id",
                String::class.java,
            )
        ).containsExactly(
            "DELTA_EVENT_MAPPING_ADDED",
            "DELTA_EVENT_MAPPING_REMOVED",
        )
    }

    @Test
    fun `should reject duplicate Delta event UUID mappings`() {
        val deltaEventUuid = UUID.randomUUID()
        deltaEventMappingRepository.addMapping(
            UUID.randomUUID(),
            "First program event",
            deltaEventUuid,
            "admin@nav.no",
        )

        assertThatThrownBy {
            deltaEventMappingRepository.addMapping(
                UUID.randomUUID(),
                "Second program event",
                deltaEventUuid,
                "admin@nav.no",
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `should add and remove an eligible Delta category with audit history`() {
        val created = deltaEligibleCategoryRepository.add(7, "Security Champions", "admin@nav.no")

        assertThat(created.categoryId).isEqualTo(7)
        assertThat(created.categoryName).isEqualTo("Security Champions")
        assertThat(deltaEligibleCategoryRepository.findAll()).containsExactly(created)
        assertThatThrownBy { deltaEligibleCategoryRepository.add(7, "Duplicate", "admin@nav.no") }
            .isInstanceOf(DuplicateKeyException::class.java)
        assertThat(deltaEligibleCategoryRepository.remove(7, "admin@nav.no")).isTrue()
        assertThat(deltaEligibleCategoryRepository.remove(7, "admin@nav.no")).isFalse()
        assertThat(deltaEligibleCategoryRepository.findAll()).isEmpty()
        assertThat(
            jdbcTemplate.queryForList(
                "SELECT action FROM program_scoring_audit ORDER BY id",
                String::class.java,
            )
        ).containsExactly("DELTA_ELIGIBLE_CATEGORY_ADDED", "DELTA_ELIGIBLE_CATEGORY_REMOVED")
    }

    @Test
    fun `should prevent removing a Delta mapping after registration credits exist`() {
        val participantId = createParticipant("person@nav.no")
        val deltaEventUuid = UUID.randomUUID()
        val mappingId = UUID.randomUUID()
        deltaEventMappingRepository.addMapping(
            mappingId,
            "Security Champion meetup",
            deltaEventUuid,
            "admin@nav.no",
        )
        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                deltaEventUuid.toString(),
                deltaEventUuid.toString(),
            )
        ).isEqualTo(CreditAwardResult.AWARDED)
        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                deltaEventUuid.toString(),
                deltaEventUuid.toString(),
            )
        ).isEqualTo(CreditAwardResult.DUPLICATE)

        assertThatThrownBy {
            deltaEventMappingRepository.removeMapping(mappingId, "admin@nav.no")
        }.isInstanceOf(DeltaEventMappingHasCreditsException::class.java)
        assertThat(deltaEventMappingRepository.findAll()).hasSize(1)
    }

    @Test
    fun `should award Delta registration credit only once per participant and event`() {
        val participantId = createParticipant("person@nav.no")
        val deltaEventUuid = UUID.randomUUID()

        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                deltaEventUuid.toString(),
                deltaEventUuid.toString(),
            )
        ).isEqualTo(CreditAwardResult.AWARDED)
        assertThat(
            repository.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                deltaEventUuid.toString(),
                deltaEventUuid.toString(),
            )
        ).isEqualTo(CreditAwardResult.DUPLICATE)
        assertThat(repository.creditsForParticipant(participantId)).hasSize(1)
    }

    @Test
    fun `should persist sanitized Delta sync outcomes and retain the last success`() {
        val firstAttempt = Instant.parse("2026-10-05T12:00:00Z")
        val firstSuccess = firstAttempt.plusSeconds(30)
        deltaScoringStatusRepository.recordStarted(firstAttempt)
        deltaScoringStatusRepository.recordSucceeded(
            firstSuccess,
            DeltaSyncSummary(
                eventsScanned = 2,
                creditsAwarded = 4,
                duplicateCredits = 1,
                unmatchedRegistrations = 3,
            ),
        )

        val secondAttempt = firstSuccess.plusSeconds(3600)
        deltaScoringStatusRepository.recordStarted(secondAttempt)
        deltaScoringStatusRepository.recordPartialFailure(
            secondAttempt,
            DeltaSyncSummary(
                eventsScanned = 1,
                creditsAwarded = 2,
                failedEvents = 1,
                failureSummary = DeltaFailure.API.summary,
            ),
        )

        val status = requireNotNull(deltaScoringStatusRepository.find())
        assertThat(status.lastAttemptAt).isEqualTo(secondAttempt)
        assertThat(status.lastSuccessAt).isEqualTo(firstSuccess)
        assertThat(status.outcome).isEqualTo(DeltaSyncOutcome.PARTIAL_FAILURE)
        assertThat(status.eventsScanned).isEqualTo(1)
        assertThat(status.creditsAwarded).isEqualTo(2)
        assertThat(status.failedEvents).isEqualTo(1)
        assertThat(status.failureSummary).isEqualTo(DeltaFailure.API.summary)
    }

    @Test
    fun `should aggregate active participants and current season credits by Oslo week and type`() {
        val activeParticipant = createParticipant("active@nav.no")
        val inactiveParticipant = createParticipant("inactive@nav.no")
        jdbcTemplate.update(
            "UPDATE program_participants SET status = 'DEACTIVATED' WHERE id = ?",
            inactiveParticipant,
        )
        val season = repository.currentSeason()
        val firstWeek = season.startsOn.plusDays(4)
        val secondWeek = season.startsOn.plusDays(11)

        insertCredit(activeParticipant, season.id, "SLACK_WEEK", "week-1", 1, firstWeek)
        insertCredit(activeParticipant, season.id, "DELTA_REGISTRATION", "event-1", 1, firstWeek)
        insertCredit(inactiveParticipant, season.id, "DELTA_REGISTRATION", "event-2", 1, secondWeek)
        jdbcTemplate.update(
            """
                INSERT INTO point_adjustments (
                    participant_id, season_id, points_delta, reason, actor_nav_no_email,
                    score_before, score_after, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            activeParticipant,
            season.id,
            2,
            "Correction",
            "admin@nav.no",
            2,
            4,
            atOsloNoon(secondWeek),
        )

        val metrics = adminDashboardRepository.metrics(season.id, season.startsOn, secondWeek)

        assertThat(metrics.activeParticipantCount).isEqualTo(1)
        assertThat(metrics.eventRegistrationCount).isEqualTo(2)
        assertThat(metrics.pointsByCreditType).containsEntry("SLACK_WEEK", 1L)
            .containsEntry("DELTA_REGISTRATION", 2L)
            .containsEntry("POINT_ADJUSTMENT", 2L)
        val weeklyTotals = metrics.weeklyPoints.associateBy(
            { it.weekStarting to it.creditType },
            { it.points },
        )
        assertThat(weeklyTotals)
            .containsEntry(firstWeek.with(java.time.DayOfWeek.MONDAY) to "SLACK_WEEK", 1L)
            .containsEntry(firstWeek.with(java.time.DayOfWeek.MONDAY) to "DELTA_REGISTRATION", 1L)
            .containsEntry(secondWeek.with(java.time.DayOfWeek.MONDAY) to "DELTA_REGISTRATION", 1L)
            .containsEntry(secondWeek.with(java.time.DayOfWeek.MONDAY) to "POINT_ADJUSTMENT", 2L)
    }

    @Test
    fun `should persist Slack outcomes and retain successful sync time without raw errors`() {
        val firstAttempt = Instant.parse("2026-10-05T12:00:00Z")
        val firstSuccess = firstAttempt.plusSeconds(30)
        slackScoringStatusRepository.recordStarted(firstAttempt)
        slackScoringStatusRepository.recordSucceeded(
            firstSuccess,
            SlackSyncSummary(messagesScanned = 12, creditsAwarded = 2, duplicateCredits = 1, unmappedAuthors = 3),
        )

        val secondAttempt = firstSuccess.plusSeconds(3600)
        slackScoringStatusRepository.recordStarted(secondAttempt)
        slackScoringStatusRepository.recordFailed(
            secondAttempt,
            "Slack activity could not be synchronized; check API access and channel configuration",
        )

        val status = requireNotNull(slackScoringStatusRepository.find())
        assertThat(status.lastAttemptAt).isEqualTo(secondAttempt)
        assertThat(status.lastSuccessAt).isEqualTo(firstSuccess)
        assertThat(status.outcome).isEqualTo(SlackSyncOutcome.FAILED)
        assertThat(status.messagesScanned).isZero()
        assertThat(status.failureSummary)
            .isEqualTo("Slack activity could not be synchronized; check API access and channel configuration")
    }

    @Test
    fun `should report a RUNNING Slack sync as interrupted when no instance holds the job lock`() {
        val jobLock = PostgresJobLock(dataSource)
        val statusService = SlackScoringStatusService(slackScoringStatusRepository, jobLock)
        slackScoringStatusRepository.recordStarted(Instant.parse("2026-10-05T12:00:00Z"))

        jobLock.tryAcquireLock(ScoringJobLockKeys.SLACK, "test")!!.use {
            assertThat(statusService.status().outcome).isEqualTo("RUNNING")
        }

        val status = statusService.status()
        assertThat(status.outcome).isEqualTo("FAILED")
        assertThat(status.failureSummary).isEqualTo(INTERRUPTED_SYNC_SUMMARY)
    }

    @Test
    fun `should report a RUNNING Delta sync as interrupted when no instance holds the job lock`() {
        val jobLock = PostgresJobLock(dataSource)
        val statusService = DeltaScoringStatusService(deltaScoringStatusRepository, jobLock, true)
        deltaScoringStatusRepository.recordStarted(Instant.parse("2026-10-05T12:00:00Z"))

        jobLock.tryAcquireLock(ScoringJobLockKeys.DELTA, "test")!!.use {
            assertThat(statusService.status().outcome).isEqualTo("RUNNING")
        }

        val status = statusService.status()
        assertThat(status.outcome).isEqualTo("FAILED")
        assertThat(status.failureSummary).isEqualTo(INTERRUPTED_SYNC_SUMMARY)
    }

    private fun createParticipant(email: String): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
                INSERT INTO program_participants (id, nav_no_email, nav_ident, email, fullname)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            email,
            email.substringBefore('@'),
            email,
            email.substringBefore('@'),
        )
        return id
    }

    private fun insertCredit(
        participantId: UUID,
        seasonId: UUID,
        creditType: String,
        uniquenessKey: String,
        points: Int,
        date: LocalDate,
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO activity_credits (
                    participant_id, season_id, credit_type, uniqueness_key, source_reference, points, awarded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            participantId,
            seasonId,
            creditType,
            uniquenessKey,
            uniquenessKey,
            points,
            atOsloNoon(date),
        )
    }

    private fun atOsloNoon(date: LocalDate) =
        java.sql.Timestamp.from(date.atTime(12, 0).atZone(ZoneId.of("Europe/Oslo")).toInstant())
}
