package navikt.appsec.securitychampionstats.common.hikari

import navikt.appsec.securitychampionstats.common.teamCatalog.dto.ResourceResponse
import navikt.appsec.securitychampionstats.stats.dto.Member
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

private val logger = LoggerFactory.getLogger(PostgresRepository::class.java)

@Repository
class PostgresRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun getAllMembers(): List<Member> {
        val sql = "SELECT id, fullname, points, email, update_at, inProgram  FROM Members"
        return try {
            jdbcTemplate.query(sql) { response, _ ->
                Member(
                    id = response.getString("id"),
                    fullname = response.getString("fullname"),
                    points = response.getInt("points"),
                    lastUpdated = response.getString("update_at"),
                    email = response.getString("email"),
                    inProgram = response.getBoolean("inProgram")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch all members due to error: ${e.message}")
            emptyList()
        }
    }

    fun addMember(fullname: String, id: String, email: String) {
        val sql = "INSERT INTO Members (id, fullname, points, email, inProgram) VALUES (?, ?, 0, ?, false)"
        try {
            jdbcTemplate.update(sql, id, fullname, email)
        } catch (e: Exception) {
            logger.error("Failed to add member due to error: ${e.message}")
        }
    }

    fun getMember(email: String): Member? {
        val sql = "SELECT id, fullname, points, email, update_at, inProgram FROM Members WHERE email = ?"

        return try {
            jdbcTemplate.query(sql,{ response, _ ->
                Member(
                    id = response.getString("id"),
                    fullname = response.getString("fullname"),
                    points = response.getInt("points"),
                    lastUpdated = response.getString("update_at"),
                    email = response.getString("email"),
                    inProgram =  response.getBoolean("inProgram")
                )
            }, email).firstOrNull() ?: Member("", "", 0, "", "")
        } catch (e: Exception) {
            logger.error("Failed to fetch member from db due to error: ${e.message}")
            null
        }
    }

    fun addMembers(members: List<ResourceResponse>) {
        members.forEach { member ->
            addMember(
                fullname = member.fullName,
                id = member.navIdent,
                email = member.email ?: "Unknown",
            )
        }
    }

    fun deleteMember(id: String) {
        val sql = "DELETE FROM Members WHERE id = ?"
        try {
            jdbcTemplate.update(sql, id)
        } catch (e: Exception) {
            logger.error("Failed to delete member due to error: ${e.message}")
        }
    }

    fun addPoints(email: String, points: Int): Int {
        val sql = "UPDATE Members SET points = points + ?, update_at = NOW() WHERE email = ?"
        return try {
            jdbcTemplate.update(sql, email, points)
            1
        } catch (e: Exception) {
            logger.error("Failed to add points due to error: ${e.message}")
            0
        }
    }
}