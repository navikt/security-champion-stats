package navikt.appsec.securitychampionapp.integrations.postgres

import navikt.appsec.securitychampionapp.testsupport.PostgresIntegrationTest
import navikt.appsec.securitychampionapp.app.events.ReminderDeliveryStatus
import navikt.appsec.securitychampionapp.integrations.postgress.EventReminderRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.Duration
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class EventReminderRepositoryTest : PostgresIntegrationTest() {
    private lateinit var repository: EventReminderRepository
    private lateinit var participants: ProgramParticipantRepository
    private val eventId = UUID.randomUUID()

    private fun postgresNow(): Instant =
        requireNotNull(jdbcTemplate.queryForObject("SELECT NOW()", Timestamp::class.java)).toInstant()

    @BeforeAll
    fun setup() {
        repository = EventReminderRepository(jdbcTemplate)
        participants = ProgramParticipantRepository(jdbcTemplate)
    }

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
        val retryDelay = Duration.ofMinutes(15)
        val before = postgresNow()
        repository.finish(delivery, ReminderDeliveryStatus.FAILED, retryAfter = retryDelay)
        val after = postgresNow()
        val databasePrecisionAllowance = Duration.ofNanos(1_000)
        assertThat(repository.deliveries(eventId).single().nextAttemptAt).isBetween(
            before.plus(retryDelay).minus(databasePrecisionAllowance),
            after.plus(retryDelay).plus(databasePrecisionAllowance),
        )
        assertThat(repository.claim(eventId, participantId, "U_SYNTHETIC", UUID.randomUUID())).isFalse()
        jdbcTemplate.update(
            "UPDATE event_reminder_deliveries SET next_attempt_at = ?",
            Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
        )
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
