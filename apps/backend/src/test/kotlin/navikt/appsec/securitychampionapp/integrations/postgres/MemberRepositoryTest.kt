package navikt.appsec.securitychampionapp.integrations.postgres

import navikt.appsec.securitychampionapp.integrations.postgress.MemberRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import navikt.appsec.securitychampionapp.testsupport.PostgresIntegrationTest
import org.springframework.dao.DataAccessException
import java.util.UUID

class MemberRepositoryTest : PostgresIntegrationTest() {
    private lateinit var repository: MemberRepository

    @BeforeAll
    fun setupRepository() {
        repository = MemberRepository(jdbcTemplate)
    }

    @Test
    fun `should read member including level from flyway migrated schema`() {
        insertMember(email = "test@nav.no", level = "2", teams = listOf("team-a", "team-b"))

        val member = repository.getMemberByEmail("test@nav.no")

        Assertions.assertThat(member).hasSize(1)
        Assertions.assertThat(member.first().level).isEqualTo("2")
        Assertions.assertThat(member.first().teams).isEqualTo(listOf("team-a", "team-b"))
    }

    @Test
    fun `should delete member when email exists`() {
        insertMember(id = "member-1", email = "test@nav.no")
        insertMember(id = "member-2", email = "keep@nav.no")

        repository.deleteMember("member-1")

        Assertions.assertThat(repository.getMemberByEmail("test@nav.no")).isEmpty()
        Assertions.assertThat(repository.getMemberByEmail("keep@nav.no")).hasSize(1)
        Assertions.assertThat(memberCount()).isEqualTo(1)
    }

    @Test
    fun `should leave existing members untouched when deleting member that does not exist`() {
        insertMember(email = "existing@nav.no")

        repository.deleteMember("missing@nav.no")

        Assertions.assertThat(repository.getMemberByEmail("existing@nav.no")).hasSize(1)
        Assertions.assertThat(memberCount()).isEqualTo(1)
    }

    @Test
    fun `should propagate member database failures`() {
        jdbcTemplate.execute("ALTER TABLE Members RENAME TO members_unavailable")
        try {
            Assertions.assertThatThrownBy { repository.getMemberByEmail("test@nav.no") }
                .isInstanceOf(DataAccessException::class.java)
            Assertions.assertThatThrownBy { repository.addMember("Test User", "id", "test@nav.no", emptyList()) }
                .isInstanceOf(DataAccessException::class.java)
        } finally {
            jdbcTemplate.execute("ALTER TABLE members_unavailable RENAME TO Members")
        }
    }

    @Test
    fun `should propagate graph database failures`() {
        jdbcTemplate.execute("ALTER TABLE SCData RENAME TO sc_data_unavailable")
        try {
            Assertions.assertThatThrownBy { repository.getSCAmountOverTime() }
                .isInstanceOf(DataAccessException::class.java)
        } finally {
            jdbcTemplate.execute("ALTER TABLE sc_data_unavailable RENAME TO SCData")
        }
    }

    private fun insertMember(
        id: String = UUID.randomUUID().toString(),
        fullname: String = "Test User",
        email: String,
        points: Int = 0,
        inProgram: Boolean = false,
        level: String = "1",
        teams: List<String> = emptyList()
    ) {
        dataSource.connection.use { connection ->
            val teamsArray = connection.createArrayOf("text", teams.toTypedArray())
            connection.prepareStatement(
                """
                    INSERT INTO Members (
                        id,
                        fullname,
                        points,
                        create_at,
                        update_at,
                        email,
                        inProgram,
                        level,
                        teams
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, id)
                statement.setString(2, fullname)
                statement.setInt(3, points)
                statement.setObject(4, java.time.OffsetDateTime.now())
                statement.setObject(5, java.time.OffsetDateTime.now())
                statement.setString(6, email)
                statement.setBoolean(7, inProgram)
                statement.setString(8, level)
                statement.setArray(9, teamsArray)
                statement.executeUpdate()
            }
        }
    }

    private fun memberCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Members", Int::class.java) ?: 0
}
