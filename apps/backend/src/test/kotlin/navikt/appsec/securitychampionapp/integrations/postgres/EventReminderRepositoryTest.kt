package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.events.ReminderDeliveryStatus
import navikt.appsec.securitychampionapp.integrations.postgress.EventReminderRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventReminderRepositoryTest {
    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: EventReminderRepository
    private lateinit var participants: ProgramParticipantRepository
    private lateinit var flyway: Flyway
    private val eventId = UUID.randomUUID()

    @BeforeAll
    fun setup() {
        dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 2
        }
        jdbc = JdbcTemplate(dataSource)
        repository = EventReminderRepository(jdbc)
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

    private fun enroll(): UUID {
        participants.enroll("synthetic@nav.no", "A12345", "synthetic@nav.no")
        return requireNotNull(participants.findByNavNoEmail("synthetic@nav.no")).id
    }

    @Test
    fun `concurrent attempts claim a recipient once and completed deliveries cannot be sent again`() {
        val participantId = enroll()
        val start = CountDownLatch(1)
        val deliveryIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val claims = Executors.newFixedThreadPool(2).use { executor ->
            val results = deliveryIds.map { deliveryId ->
                executor.submit<Boolean> {
                    start.await()
                    repository.claim(eventId, participantId, "U_SYNTHETIC", deliveryId)
                }
            }
            start.countDown()
            results.map { it.get() }
        }
        assertThat(claims.count { it }).isEqualTo(1)
        val claimed = deliveryIds[claims.indexOf(true)]
        assertThat(repository.deliveries(eventId).single().status).isEqualTo(ReminderDeliveryStatus.SENDING)
        repository.finish(claimed, ReminderDeliveryStatus.SENT, "123.456")
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", UUID.randomUUID())).isFalse()
        assertThat(repository.deliveries(eventId).single().status).isEqualTo(ReminderDeliveryStatus.SENT)
    }

    @Test
    fun `uncertain and interrupted deliveries block subsequent attempts`() {
        val participantId = enroll()
        val delivery = UUID.randomUUID()
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", delivery)).isTrue()
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", UUID.randomUUID())).isFalse()
        repository.finish(delivery, ReminderDeliveryStatus.UNCERTAIN)
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", UUID.randomUUID())).isFalse()
    }

    @Test
    fun `known rejections persist a retry delay and allow a new attempt only after the delay`() {
        val participantId = enroll()
        val delivery = UUID.randomUUID()
        repository.claim(eventId, participantId, "U_SYNTHETIC", delivery)
        repository.finish(delivery, ReminderDeliveryStatus.FAILED, retryAfter = Duration.ofMinutes(15))
        assertThat(repository.deliveries(eventId).single().nextAttemptAt).isAfter(Instant.now().plusSeconds(890))
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", UUID.randomUUID())).isFalse()
        jdbc.update("UPDATE event_reminder_deliveries SET next_attempt_at = NOW() - INTERVAL '1 second'")
        val retry = UUID.randomUUID()
        assertThat(repository.claim(eventId, participantId, "U_CHANGED", retry)).isTrue()
        repository.finish(retry, ReminderDeliveryStatus.SENT, "456.789")
        assertThat(repository.deliveries(eventId).single().status).isEqualTo(ReminderDeliveryStatus.SENT)
    }

    @Test
    fun `inactive recipients cannot be claimed and permanent deletion removes delivery records`() {
        val participantId = enroll()
        participants.leave("synthetic@nav.no")
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", UUID.randomUUID())).isFalse()
        participants.rejoin("synthetic@nav.no")
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", UUID.randomUUID())).isTrue()
        participants.permanentlyDelete(participantId)
        assertThat(repository.deliveries(eventId)).isEmpty()
    }
}
