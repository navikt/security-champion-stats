package navikt.appsec.securitychampionstats.common.hikari

import navikt.appsec.securitychampionstats.stats.dto.Member
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

private val logger = LoggerFactory.getLogger(PostgresRepository::class.java)

@Repository
class PostgresRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private fun fetchMembers(query: String, vararg args: Any): List<Member> {
        return try {
            val rowMapper = RowMapper { rs, _ ->
                Member(
                    id = rs.getString("id"),
                    fullname = rs.getString("fullname"),
                    points = rs.getInt("points"),
                    lastUpdated = rs.getString("update_at"),
                    email = rs.getString("email"),
                    inProgram = rs.getBoolean("inProgram")
                )
            }
            if (args.isEmpty()) {
                jdbcTemplate.query(query, rowMapper)
            } else {
                jdbcTemplate.query(query, rowMapper, *args)
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch members: ${e.message}")
            emptyList()
        }
    }

    private fun updateMember(query: String, vararg args: Any) {
        try {
            jdbcTemplate.update(query, *args)
        } catch (e: Exception) {
            logger.error("Failed to update member due to error: ${e.message}")
        }
    }

    fun getAllMembersInProgram(): List<Member> {
        val query = "SELECT id, fullname, points, email, update_at, inProgram FROM Members WHERE inProgram = true"
        return fetchMembers(query)
    }

    fun getAllMembers(): List<Member> {
        val query = "SELECT id, fullname, points, email, update_at, inProgram  FROM Members"
        return fetchMembers(query)
    }

    fun addMember(fullname: String, id: String, email: String) {
        val query = "INSERT INTO Members (id, fullname, points, email, inProgram) VALUES (?, ?, 0, ?, false)"
        updateMember(query, id, fullname, email)
    }

    fun getMemberByEmail(email: String): Member? {
        val query = "SELECT id, fullname, points, email, update_at, inProgram FROM Members WHERE email = ?"
        return fetchMembers(query, email).firstOrNull()
    }

    fun deleteMember(email: String) {
        val query = "DELETE FROM Members WHERE email = ?"
        updateMember(query, email)
    }

    fun addPoints(email: String, points: Int){
        val query = "UPDATE Members SET points = points + ?, update_at = NOW() WHERE email = ?"
        updateMember(query, points, email)
    }

    fun updateInProgram(email: String, inProgram: Boolean) {
        val query = "UPDATE Members SET inProgram = $inProgram, update_at = NOW() WHERE email = ?"
        updateMember(query, email)
    }
}