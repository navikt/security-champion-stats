package navikt.appsec.securitychampionstats.integration.postgres

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
        val sql = "SELECT id, fullname, points, email FROM Members"
        return try {
            jdbcTemplate.query(sql) { response, _ ->
                Member(
                    id = response.getString("id"),
                    fullname = response.getString("fullname"),
                    points = response.getInt("points"),
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch all members due to error: ${e.message}")
            emptyList()
        }
    }

    fun addMember(fullname: String, id: String) {
        val sql = "INSERT INTO Members (id, fullname, points) VALUES (?, ?, ?, 0)"
        try {
            jdbcTemplate.update(sql, id, fullname)
        } catch (e: Exception) {
            logger.error("Failed to add member due to error: ${e.message}")
        }
    }

    fun addMembers(members: List<navikt.appsec.securitychampionstats.integration.teamCatalog.dto.Member>) {
        members.forEach { member ->
            addMember(
                fullname = member.resource.fullName,
                id = member.resource.navIdent
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

    fun addPoints(id: String, points: Int) {
        val sql = "UPDATE Members SET points = points + ? WHERE id = ?"
        try {
            jdbcTemplate.update(sql, id, points)
        } catch (e: Exception) {
            logger.error("Failed to add points due to error: ${e.message}")
        }
    }
}