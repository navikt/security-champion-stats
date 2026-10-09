package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.participation.DeactivationReason
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.SlackChannelParticipationRepository
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlackChannelParticipationRepositoryTest {
    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: SlackChannelParticipationRepository
    private lateinit var participants: ProgramParticipantRepository
    private lateinit var flyway: Flyway

    @BeforeAll
    fun setup() {
        dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 2
        }
        jdbc = JdbcTemplate(dataSource)
        repository = SlackChannelParticipationRepository(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)))
        participants = ProgramParticipantRepository(jdbc)
        flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load()
    }

    @BeforeEach
    fun reset() {
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun close() = dataSource.close()

    @Test
    fun `departure deactivates with a reason, retains history and queues one notice`() {
        val id = enroll("leaver")

        val changes = repository.apply("C_CHANNEL", now, departure(id))

        assertThat(changes.deactivated).containsExactly(id)
        val participant = requireNotNull(participants.findById(id))
        assertThat(participant.status).isEqualTo(ParticipationStatus.DEACTIVATED)
        assertThat(participant.deactivationReason).isEqualTo(DeactivationReason.SLACK_CHANNEL_DEPARTURE)
        assertThat(repository.dueNotices("C_CHANNEL").map { it.participantId to it.slackUserId })
            .containsExactly(id to "U_LEAVER")
        assertThat(
            jdbc.queryForObject(
                "SELECT after_values ->> 'status' FROM program_participant_audit WHERE participant_id = ?",
                String::class.java, id,
            ),
        ).isEqualTo("DEACTIVATED")
        assertThat(repository.attentionItems("C_CHANNEL").single())
            .extracting({ it.category }, { it.notificationStatus })
            .containsExactly(ChannelAttentionCategory.DEACTIVATED_AFTER_LEAVING, ChannelNoticeStatus.PENDING)

        assertThat(repository.apply("C_CHANNEL", now, departure(id)).deactivated).isEmpty()
        assertThat(repository.dueNotices("C_CHANNEL")).hasSize(1)
    }

    @Test
    fun `return reactivates only channel-caused deactivation and cancels the unsent notice`() {
        val departed = enroll("departed")
        val adminDeactivated = enroll("admin")
        repository.apply("C_CHANNEL", now, departure(departed))
        participants.updateStatus(adminDeactivated, false, "admin@nav.no")

        val changes = repository.apply(
            "C_CHANNEL", now,
            ChannelCheckPlan(
                listOf(ChannelObservation(departed, "U_DEPARTED", true, null)),
                emptyMap(),
                setOf(departed, adminDeactivated),
            ),
        )

        assertThat(changes.reactivated).containsExactly(departed)
        assertThat(participants.findById(departed)?.status).isEqualTo(ParticipationStatus.ACTIVE)
        assertThat(participants.findById(departed)?.deactivationReason).isNull()
        assertThat(participants.findById(adminDeactivated)?.status).isEqualTo(ParticipationStatus.DEACTIVATED)
        assertThat(repository.dueNotices("C_CHANNEL")).isEmpty()
        assertThat(repository.attentionItems("C_CHANNEL")).isEmpty()
    }

    @Test
    fun `administrator status changes remove the channel-departure reason`() {
        val id = enroll("leaver")
        repository.apply("C_CHANNEL", now, departure(id))

        participants.updateStatus(id, false, "admin@nav.no")

        assertThat(participants.findById(id)?.deactivationReason).isNull()
        assertThat(repository.apply("C_CHANNEL", now, ChannelCheckPlan(emptyList(), emptyMap(), setOf(id))).reactivated)
            .isEmpty()
    }

    @Test
    fun `overview lists absent and unresolved active participants and drops participants outside the check`() {
        val absent = enroll("absent")
        val unresolved = enroll("unresolved")
        val present = enroll("present")
        repository.apply(
            "C_CHANNEL", now,
            ChannelCheckPlan(
                listOf(
                    ChannelObservation(absent, "U_ABSENT", false, now),
                    ChannelObservation(unresolved, null, null, null),
                    ChannelObservation(present, "U_PRESENT", true, null),
                ),
                emptyMap(),
                emptySet(),
            ),
        )

        assertThat(repository.attentionItems("C_CHANNEL").associate { it.participantId to it.category })
            .containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    absent to ChannelAttentionCategory.NOT_IN_CHANNEL,
                    unresolved to ChannelAttentionCategory.IDENTITY_UNRESOLVED,
                ),
            )
        assertThat(repository.attentionItems("C_OTHER")).isEmpty()

        repository.apply("C_CHANNEL", now, ChannelCheckPlan(emptyList(), emptyMap(), emptySet()))
        assertThat(repository.observations("C_CHANNEL")).isEmpty()
    }

    @Test
    fun `check status records success, partial delivery and failure`() {
        repository.recordStarted("C_CHANNEL", now)
        repository.recordCompleted("C_CHANNEL", now, partial = true)
        assertThat(repository.status("C_CHANNEL")?.outcome).isEqualTo("PARTIAL_FAILURE")

        repository.recordFailed("C_CHANNEL", now, "Slack conversations.members failed")
        val status = requireNotNull(repository.status("C_CHANNEL"))
        assertThat(status.outcome).isEqualTo("FAILED")
        assertThat(status.lastSuccessAt).isNotNull()
    }

    private fun departure(id: UUID) = ChannelCheckPlan(
        listOf(ChannelObservation(id, "U_${name(id).uppercase()}", false, now)),
        mapOf(id to "U_${name(id).uppercase()}"),
        emptySet(),
    )

    private fun name(id: UUID) = requireNotNull(participants.findById(id)).fullname

    private fun enroll(name: String): UUID {
        participants.enroll("$name@nav.no", "A12345", "$name@nav.no", name)
        return requireNotNull(participants.findByNavNoEmail("$name@nav.no")).id
    }
}
