package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.membership.MembershipAnnouncementKind
import navikt.appsec.securitychampionapp.app.membership.MembershipDeliveryStatus
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.SlackMembershipRepository
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlackMembershipRepositoryTest {
    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: SlackMembershipRepository
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
        repository = SlackMembershipRepository(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)))
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
    fun `baseline is silent and later membership changes are recorded once`() {
        val first = enroll("first")
        repository.observe("S_GROUP", mapOf(first to "U_FIRST"))
        assertThat(repository.announcements("S_GROUP")).isEmpty()

        val second = enroll("second")
        repository.observe("S_GROUP", mapOf(second to "U_SECOND"))
        repository.observe("S_GROUP", mapOf(second to "U_SECOND"))

        val announcements = repository.announcements("S_GROUP")
        assertThat(announcements).hasSize(2)
        assertThat(announcements.map { it.kind })
            .containsExactlyInAnyOrder(MembershipAnnouncementKind.WELCOME, MembershipAnnouncementKind.REMOVAL)
        assertThat(announcements.map { it.status }).containsOnly(MembershipDeliveryStatus.PENDING)
    }

    @Test
    fun `interrupted deliveries require explicit resolution and delivered messages do not reappear`() {
        val first = enroll("first")
        repository.observe("S_GROUP", mapOf(first to "U_FIRST"))
        val second = enroll("second")
        repository.observe("S_GROUP", mapOf(first to "U_FIRST", second to "U_SECOND"))
        val announcement = repository.announcements("S_GROUP").single()

        repository.updateDelivery(announcement.id, MembershipDeliveryStatus.SENDING)
        repository.recoverInterruptedDeliveries("S_GROUP")
        assertThat(repository.announcements("S_GROUP").single().status).isEqualTo(MembershipDeliveryStatus.UNCERTAIN)
        assertThat(repository.resolveUncertain("S_OTHER", announcement.id, true)).isFalse()
        assertThat(repository.resolveUncertain("S_GROUP", announcement.id, true)).isTrue()
        repository.updateDelivery(announcement.id, MembershipDeliveryStatus.SENT, "123.456")
        repository.observe("S_GROUP", mapOf(first to "U_FIRST", second to "U_SECOND"))
        assertThat(repository.announcements("S_GROUP")).isEmpty()
    }

    @Test
    fun `leave and rejoin supersede pending notices and permanent deletion erases state`() {
        val first = enroll("first")
        val second = enroll("second")
        repository.observe("S_GROUP", mapOf(first to "U_FIRST", second to "U_SECOND"))
        repository.observe("S_GROUP", mapOf(first to "U_FIRST"))
        repository.observe("S_GROUP", mapOf(first to "U_FIRST", second to "U_SECOND"))
        assertThat(repository.announcements("S_GROUP").single().kind).isEqualTo(MembershipAnnouncementKind.WELCOME)

        participants.permanentlyDelete(second)

        assertThat(repository.announcements("S_GROUP")).isEmpty()
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM slack_membership_snapshot WHERE participant_id = ?", Int::class.java, second,
        )).isZero()
    }

    @Test
    fun `reconciliation rollback preserves previous snapshot and creates no partial announcements`() {
        val first = enroll("first")
        repository.observe("S_GROUP", mapOf(first to "U_FIRST"))
        org.assertj.core.api.Assertions.assertThatThrownBy {
            repository.observe("S_GROUP", mapOf(UUID.randomUUID() to "U_INVALID"))
        }.isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)

        repository.observe("S_GROUP", mapOf(first to "U_FIRST"))
        assertThat(repository.announcements("S_GROUP")).isEmpty()
    }

    private fun enroll(name: String): UUID {
        participants.enroll("$name@nav.no", "A12345", "$name@nav.no")
        return requireNotNull(participants.findByNavNoEmail("$name@nav.no")).id
    }
}
