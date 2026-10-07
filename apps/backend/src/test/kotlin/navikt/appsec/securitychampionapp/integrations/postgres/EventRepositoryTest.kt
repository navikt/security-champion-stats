package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.PlaybookEventRepository
import navikt.appsec.securitychampionapp.integrations.playbook.PlaybookEvent
import org.assertj.core.api.Assertions
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.dao.DataAccessException
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.dao.DuplicateKeyException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventRepositoryTest {

    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    lateinit var dataSource: HikariDataSource
    lateinit var jdbcTemplate: JdbcTemplate
    lateinit var repository: EventRepository
    lateinit var flyway: Flyway

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
        repository = EventRepository(jdbcTemplate)
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
    fun setup() {
        flyway.clean()
        flyway.migrate()
    }

    @Test
    fun `should prevent duplicate events from concurrent submissions`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val attempts = (1..2).map {
                executor.submit<Boolean> {
                    ready.countDown()
                    start.await()
                    try {
                        repository.addEvent(testEvent())
                        true
                    } catch (_: DuplicateKeyException) {
                        false
                    }
                }
            }
            val allReady = ready.await(10, TimeUnit.SECONDS)
            start.countDown()
            Assertions.assertThat(allReady).isTrue()
            Assertions.assertThat(attempts.map { it.get(10, TimeUnit.SECONDS) })
                .containsExactlyInAnyOrder(true, false)
        }
        Assertions.assertThat(repository.getAllEvents()).hasSize(1)
    }

    @Test
    fun `should report legacy duplicates during migration without deleting events`() {
        flyway.clean()
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("17.0")
            .load()
            .migrate()
        insertEvent(name = "Security meetup", location = "Oslo")
        insertEvent(name = " SECURITY MEETUP ", location = " OSLO ")

        Assertions.assertThatThrownBy { flyway.migrate() }
            .isInstanceOf(FlywayException::class.java)
            .hasStackTraceContaining("Duplicate program events exist")
            .hasStackTraceContaining("no events have been deleted")
        Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM Events", Int::class.java))
            .isEqualTo(2)
    }

    private fun testEvent() = Event(
        id = UUID.randomUUID().toString(),
        name = "Security meetup",
        description = "Synthetic event",
        startDate = "2026-11-01T09:00:00Z",
        endDate = "2026-11-01T10:00:00Z",
        location = "Oslo",
        type = "meetup",
    )

    @Test
    fun `should reject duplicate program events with normalized name start and location`() {
        val event = testEvent()
        repository.addEvent(event)

        val duplicate = event.copy(
            id = UUID.randomUUID().toString(),
            name = " SECURITY MEETUP ",
            startDate = "2026-11-01T10:00:00+01:00",
            location = " OSLO ",
        )
        Assertions.assertThatThrownBy { repository.addEvent(duplicate) }
            .isInstanceOf(DuplicateKeyException::class.java)
        Assertions.assertThat(repository.getAllEvents()).hasSize(1)
        repository.addEvent(event.copy(
            id = UUID.randomUUID().toString(),
            location = "Bergen",
        ))
        repository.addEvent(event.copy(
            id = UUID.randomUUID().toString(),
            startDate = "2026-11-02T09:00:00Z",
            endDate = "2026-11-02T10:00:00Z",
        ))
    }

    @Test
    fun `should read all events from flyway migrated schema`() {
        val start = Instant.parse("2026-03-01T09:00:00Z")
        val end = Instant.parse("2026-03-01T15:00:00Z")
        insertEvent(
            name = "Security Champion Summit",
            description = "Yearly gathering for security champions",
            startDateTime = start,
            endDateTime = end,
            location = "Oslo",

        )

        val events = repository.getAllEvents()

        Assertions.assertThat(events).hasSize(1)
        val event = events.first()
        Assertions.assertThat(event.name).isEqualTo("Security Champion Summit")
        Assertions.assertThat(event.description).isEqualTo("Yearly gathering for security champions")
        Assertions.assertThat(event.location).isEqualTo("Oslo")
        Assertions.assertThat(Instant.parse(event.startDate)).isEqualTo(start)
        Assertions.assertThat(Instant.parse(event.endDate)).isEqualTo(end)
    }

    @Test
    fun `should read multiple events including default flags`() {
        insertEvent(name = "Workshop", externalEvent = true, deltaEvent = false)
        insertEvent(name = "Internal Meetup")

        val events = repository.getAllEvents()

        Assertions.assertThat(events).hasSize(2)
        Assertions.assertThat(events.map { it.name }).containsExactlyInAnyOrder("Workshop", "Internal Meetup")
        val workshop = events.first { it.name == "Workshop" }
        Assertions.assertThat(workshop.externalEvent).isTrue()
        Assertions.assertThat(workshop.deltaEvent).isFalse()
        val internalMeetup = events.first { it.name == "Internal Meetup" }
        Assertions.assertThat(internalMeetup.externalEvent).isFalse()
        Assertions.assertThat(internalMeetup.deltaEvent).isTrue()
    }

    @Test
    fun `should insert and then update a Delta event with its link`() {
        val event = testEvent().copy(link = "https://delta.nav.no/event/abc")
        repository.upsertDeltaEvent(event)
        repository.upsertDeltaEvent(event.copy(name = "Renamed meetup", location = "Bergen"))

        val stored = repository.getAllEvents().single()
        Assertions.assertThat(stored.name).isEqualTo("Renamed meetup")
        Assertions.assertThat(stored.location).isEqualTo("Bergen")
        Assertions.assertThat(stored.link).isEqualTo("https://delta.nav.no/event/abc")
        Assertions.assertThat(stored.deltaEvent).isTrue()
    }

    @Test
    fun `should return empty result when no events exist`() {
        Assertions.assertThat(repository.getAllEvents()).isEmpty()
    }

    @Test
    fun `should propagate event database failures`() {
        jdbcTemplate.execute("ALTER TABLE Events RENAME TO events_unavailable")

        Assertions.assertThatThrownBy { repository.getAllEvents() }
            .isInstanceOf(DataAccessException::class.java)
        Assertions.assertThatThrownBy { repository.addEvent(testEvent()) }
            .isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `should replace playbook snapshots without altering own events and roll back failed replacements`() {
        val playbook = PlaybookEventRepository(jdbcTemplate)
        val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))
        val original = PlaybookEvent(
            "playbook:course", "Course", "2026-10-20", "2026-10-22", "Alle", "https://example.org",
        )
        repository.addEvent(testEvent())
        transaction.executeWithoutResult { playbook.replaceSnapshot(listOf(original)) }
        Assertions.assertThat(playbook.findAll()).containsExactly(original)

        Assertions.assertThatThrownBy {
            transaction.executeWithoutResult {
                playbook.replaceSnapshot(listOf(original.copy(endDate = "2026-10-19")))
            }
        }.isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)
        Assertions.assertThat(playbook.findAll()).containsExactly(original)

        val updated = original.copy(title = "Renamed", startDate = "2026-10-21")
        transaction.executeWithoutResult { playbook.replaceSnapshot(listOf(updated)) }
        Assertions.assertThat(playbook.findAll()).containsExactly(updated)
        transaction.executeWithoutResult { playbook.replaceSnapshot(emptyList()) }
        Assertions.assertThat(playbook.findAll()).isEmpty()
        Assertions.assertThat(repository.getAllEvents()).hasSize(1)
    }

    private fun insertEvent(
        id: String = UUID.randomUUID().toString(),
        name: String,
        description: String = "Test description",
        startDateTime: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        endDateTime: Instant = Instant.parse("2026-01-01T01:00:00Z"),
        externalEvent: Boolean = false,
        deltaEvent: Boolean = true,
        location: String = "Oslo",
        eventType: String = "WORKSHOP",
        amountOfPeopleJoined: Int = 0
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    INSERT INTO Events (
                        id,
                        name,
                        description,
                        start_date,
                        end_date,
                        external_event,
                        delta_event,
                        location,
                        event_type,
                        amount_of_people_joined
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(id))
                statement.setString(2, name)
                statement.setString(3, description)
                statement.setObject(4, OffsetDateTime.ofInstant(startDateTime, ZoneOffset.UTC))
                statement.setObject(5, OffsetDateTime.ofInstant(endDateTime, ZoneOffset.UTC))
                statement.setBoolean(6, externalEvent)
                statement.setBoolean(7, deltaEvent)
                statement.setString(8, location)
                statement.setString(9, eventType)
                statement.setInt(10, amountOfPeopleJoined)
                statement.executeUpdate()
            }
        }
    }
}
