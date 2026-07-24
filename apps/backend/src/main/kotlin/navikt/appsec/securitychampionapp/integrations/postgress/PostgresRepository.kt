package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.api.dto.SCdata
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlMember
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlTextArray
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant

private val logger = LoggerFactory.getLogger(PostgresRepository::class.java)

@Repository
class PostgresRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private fun queryMembersData(query: String, vararg args: Any): List<SqlMember> {
        return try {
            val rowMapper = RowMapper { rs, _ ->
                val teams = (rs.getArray("teams")?.array as? Array<*>)
                    ?.mapNotNull { team -> team?.toString() }
                    ?: emptyList()
                SqlMember(
                    id = rs.getString("id"),
                    fullname = rs.getString("fullname"),
                    points = rs.getInt("points"),
                    lastUpdated = rs.getString("update_at"),
                    email = rs.getString("email"),
                    inProgram = rs.getBoolean("inProgram"),
                    level = rs.getString("level") ?: "1",
                    teams = teams
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

    private fun querySCData(query: String, vararg args: Any): List<SCdata> {
        return try {
            val rowMapper = RowMapper { rs, _ ->
                SCdata(
                    timestamp = rs.getString("id"),
                    amount = rs.getInt("amount")
                )
            }
            if (args.isEmpty()) {
                jdbcTemplate.query(query, rowMapper)
            } else {
                jdbcTemplate.query(query, rowMapper, *args)
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch SC data: ${e.message}")
            emptyList()
        }
    }

    private fun executeUpdate(query: String, vararg args: Any) {
        try {
            jdbcTemplate.update { connection ->
                connection.prepareStatement(query).apply {
                    args.forEachIndexed { index, value ->
                        val paramIndex = index + 1

                        when (value) {
                            null -> setObject(paramIndex, null)
                            is SqlTextArray -> setArray(
                                paramIndex,
                                connection.createArrayOf(
                                    "text",
                                    value.value.toTypedArray()
                                )
                            )
                            else -> setObject(paramIndex, value)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Failed SQL: {}, arguments: {}",
                query,
                args.contentToString(),
                e
            )
        }
    }

    fun getAllMembersInProgram(): List<SqlMember> {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams FROM Members WHERE inProgram = true"
        return queryMembersData(query)
    }

    fun getAllMembers(): List<SqlMember> {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams FROM Members"
        return queryMembersData(query)
    }

    fun addMember(fullname: String, id: String, email: String, teams: List<String>) {
        val query = "INSERT INTO Members (id, fullname, points, email, inProgram, level, teams) VALUES (?, ?, 0, ?, false, '1', ?)"
        executeUpdate(query, id, fullname, email, SqlTextArray(teams))
    }

    fun getMemberByEmail(email: String): SqlMember? {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams FROM Members WHERE email = ?"
        return queryMembersData(query, email).firstOrNull()
    }

    fun deleteMember(id: String) {
        val query = "DELETE FROM Members WHERE id = ?"
        executeUpdate(query, id)
    }

    fun addPoints(email: String, points: Int){
        val query = "UPDATE Members SET points = points + ?, update_at = NOW() WHERE email = ?"
        executeUpdate(query, points, email)
    }

    fun resetAllPointsAndLevels(): Int {
        val query = "UPDATE Members SET points = 0, level = '1', update_at = NOW()"
        return try {
            jdbcTemplate.update(query)
        } catch (e: Exception) {
            logger.error("Failed to reset members due to error: ${e.message}")
            0
        }
    }

    fun updateInProgram(email: String, inProgram: Boolean) {
        val query = "UPDATE Members SET inProgram = $inProgram, update_at = NOW() WHERE email = ?"
        executeUpdate(query, email)
    }

    fun getSCAmountOverTime(startDate: Instant? = null, endDate: Instant? = null ): List<SCdata> {
        return if (startDate == null || endDate == null) {
            val query = "SELECT id, amount FROM SCData"
            querySCData(query)
        } else {
            val query = "SELECT id, amount FROM SCData where id BETWEEN ? AND ?"
            querySCData(query, startDate.toString(), endDate.toString())
        }
    }
}
