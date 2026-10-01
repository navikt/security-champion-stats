package navikt.appsec.securitychampionapp.integrations.postgres

import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import org.assertj.core.api.Assertions
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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

        val response = repository.getAllEvents()

        Assertions.assertThat(response.isOk).isTrue()
        Assertions.assertThat(response.queryResult).hasSize(1)
        val event = response.queryResult!!.first()
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

        val response = repository.getAllEvents()

        Assertions.assertThat(response.isOk).isTrue()
        Assertions.assertThat(response.queryResult).hasSize(2)
        Assertions.assertThat(response.queryResult!!.map { it.name }).containsExactlyInAnyOrder("Workshop", "Internal Meetup")
        val workshop = response.queryResult.first { it.name == "Workshop" }
        Assertions.assertThat(workshop.externalEvent).isTrue()
        Assertions.assertThat(workshop.deltaEvent).isFalse()
        val internalMeetup = response.queryResult.first { it.name == "Internal Meetup" }
        Assertions.assertThat(internalMeetup.externalEvent).isFalse()
        Assertions.assertThat(internalMeetup.deltaEvent).isTrue()
    }

    @Test
    fun `should return empty result when no events exist`() {
        val response = repository.getAllEvents()

        Assertions.assertThat(response.isOk).isTrue()
        Assertions.assertThat(response.queryResult).isEmpty()
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
