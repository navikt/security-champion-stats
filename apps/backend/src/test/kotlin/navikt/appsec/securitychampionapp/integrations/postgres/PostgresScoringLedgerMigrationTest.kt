package navikt.appsec.securitychampionapp.integrations.postgres

import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresScoringLedger
import navikt.appsec.securitychampionapp.testsupport.PostgresTestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class PostgresScoringLedgerMigrationTest {
    private lateinit var database: PostgresTestDatabase.IsolatedDatabase
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var repository: PostgresScoringLedger

    @BeforeEach
    fun createDatabase() {
        database = PostgresTestDatabase.isolated(maxPoolSize = 2)
        jdbcTemplate = JdbcTemplate(database.dataSource)
        repository = PostgresScoringLedger(jdbcTemplate)
    }

    @AfterEach
    fun dropDatabase() {
        database.close()
    }

    @Test
    fun `should backfill event claims Delta mappings and resolvable GitHub sources during migration`() {
        val flyway = database.flyway()
        flyway.clean()
        Flyway.configure().dataSource(database.dataSource).locations("classpath:db/migration").target("32.0").load().migrate()
        val participantId = createParticipant("migration-history@nav.no")
        val season = repository.currentSeason()
        val claimId = UUID.randomUUID()
        val deltaId = UUID.randomUUID()
        val eventAt = season.startsOn.atTime(12, 0).atZone(ZoneId.of("Europe/Oslo")).toInstant()
        jdbcTemplate.update(
            """
                INSERT INTO event_contribution_claims (
                    id, submitter_id, season_id, name, description, start_date, end_date,
                    location, event_type, external_event, links, invitation_evidence
                ) VALUES (?, ?, ?, 'Security meetup', 'Security content', ?, ?, 'Oslo', 'MEETUP',
                    FALSE, '["https://example.org/meetup"]', 'Invited in advance')
            """.trimIndent(),
            claimId, participantId, season.id, Timestamp.from(eventAt), Timestamp.from(eventAt.plusSeconds(3600)),
        )
        jdbcTemplate.update(
            """INSERT INTO program_delta_event_mappings
                (program_event_name, delta_event_uuid, created_by_nav_no_email)
                VALUES ('Delta workshop', ?, 'admin@nav.no')""",
            deltaId,
        )
        insertCredit(participantId, season.id, "SECURITY_EVENT_CONTRIBUTION", "event-claim:$claimId", 5, season.startsOn)
        insertCredit(participantId, season.id, "DELTA_REGISTRATION", deltaId.toString(), 1, season.startsOn)
        insertCredit(participantId, season.id, "GITHUB_PULL_REQUEST", "navikt/security-playbook:pr:42", 3, season.startsOn)
        insertCredit(participantId, season.id, "GITHUB_COMMIT", "navikt/security-playbook:commit:${"a".repeat(40)}", 1, season.startsOn)

        flyway.migrate()

        val history = repository.scoreHistoryPage(participantId, null, "credit", null, 25)
        val claim = history.single { it.creditType == ActivityCreditType.SECURITY_EVENT_CONTRIBUTION }
        assertThat(claim.displayName).isEqualTo("Security meetup")
        assertThat(claim.sourceOccurredAt).isEqualTo(eventAt)
        assertThat(claim.sourceUrl).isEqualTo("https://example.org/meetup")
        val delta = history.single { it.creditType == ActivityCreditType.DELTA_REGISTRATION }
        assertThat(delta.displayName).isEqualTo("Delta workshop")
        assertThat(delta.sourceUrl).isEqualTo("https://delta.nav.no/event/$deltaId")
        assertThat(delta.sourceOccurredAt).isNull()
        assertThat(history.single { it.creditType == ActivityCreditType.GITHUB_PULL_REQUEST }.sourceUrl)
            .isEqualTo("https://github.com/navikt/security-playbook/pull/42")
        assertThat(history.single { it.creditType == ActivityCreditType.GITHUB_COMMIT }.displayName)
            .isEqualTo("navikt/security-playbook commit aaaaaaa")
        assertThat(repository.scoreForParticipant(participantId, season.id)).isEqualTo(10)
    }

    private fun createParticipant(email: String): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
                INSERT INTO program_participants (id, nav_no_email, nav_ident, email, fullname)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id, email, email.substringBefore('@'), email, email.substringBefore('@'),
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
            participantId, seasonId, creditType, uniquenessKey, uniquenessKey, points,
            Timestamp.from(date.atTime(12, 0).atZone(ZoneId.of("Europe/Oslo")).toInstant()),
        )
    }
}
