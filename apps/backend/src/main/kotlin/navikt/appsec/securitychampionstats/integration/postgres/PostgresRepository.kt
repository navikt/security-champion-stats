package navikt.appsec.securitychampionstats.integration.postgres

import navikt.appsec.securitychampionstats.integration.teamCatalog.dto.ResourceResponse
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
        val sql = "SELECT id, fullname, points FROM Members"
        return try {
            jdbcTemplate.query(sql) { response, _ ->
                Member(
                    id = response.getString("id"),
                    fullname = response.getString("fullname"),
                    points = response.getInt("points"),
                    lastUpdated = null,
                    email = response.getString("email")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch all members due to error: ${e.message}")
            emptyList()
        }
    }

    fun addMember(fullname: String, id: String, email: String) {
        val sql = "INSERT INTO Members (id, fullname, points, email) VALUES (?, ?, 0, ?)"
        try {
            jdbcTemplate.update(sql, id, fullname, email)
        } catch (e: Exception) {
            logger.error("Failed to add member due to error: ${e.message}")
        }
    }

    fun getMember(id: String, email: String = ""): Member? {
        val sql = if (email.isEmpty()) { "SELECT id, fullname, points FROM Members WHERE id = ?"}
        else { "SELECT id, fullname, points FROM Members WHERE email = ?" }

        return try {
            jdbcTemplate.query(sql,{ response, _ ->
                Member(
                    id = response.getString("id"),
                    fullname = response.getString("fullname"),
                    points = response.getInt("points"),
                    lastUpdated = response.getString("update_at"),
                    email = response.getString("email")
                )
            }, email.ifEmpty { id }).firstOrNull() ?: Member("", "", 0, "", "")
        } catch (e: Exception) {
            logger.error("Failed to fetch member from db due to error: ${e.message}")
            null
        }
    }

    fun addMembers(members: List<ResourceResponse>) {
        members.forEach { member ->
            addMember(
                fullname = member.fullName ?: "Unknown",
                id = member.navIdent ?: "Unknown",
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

    fun addPoints(email: String, points: Int) {
        val sql = "UPDATE Members SET points = points + ? WHERE email = ?"
        try {
            jdbcTemplate.update(sql, email, points)
        } catch (e: Exception) {
            logger.error("Failed to add points due to error: ${e.message}")
        }
    }
}