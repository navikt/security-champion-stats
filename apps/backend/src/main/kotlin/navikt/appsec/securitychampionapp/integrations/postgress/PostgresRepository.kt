package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.api.dto.SCdata
import navikt.appsec.securitychampionapp.integrations.postgress.dto.DatabaseQueryResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.DatabaseUpdateResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlMember
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlTextArray
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant


@Repository
class PostgresRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private fun queryMembersData(query: String, vararg args: Any): DatabaseQueryResponse {
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
                DatabaseQueryResponse(
                    isOk = true,
                    jdbcTemplate.query(query, rowMapper)
                )
            } else {
                DatabaseQueryResponse(
                    isOk = true,
                    jdbcTemplate.query(query, rowMapper, *args)

                )
            }
        } catch (e: Exception) {
            DatabaseQueryResponse(
                isOk = false,
                emptyList(),
                error = "Failed to fetch members: ${e.message}"
            )
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
            emptyList()
        }
    }

    private fun executeUpdate(query: String, vararg args: Any): DatabaseUpdateResponse {
        try {
            jdbcTemplate.update { connection ->
                connection.prepareStatement(query).apply {
                    args.forEachIndexed { index, value ->
                        val paramIndex = index + 1

                        when (value) {
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
            return DatabaseUpdateResponse(isOk = true)
        } catch (e: Exception) {
            return DatabaseUpdateResponse(
                isOk = false,
                error = "Failed to execute update: ${e.message}"
            )
        }
    }

    fun getAllMembersInProgram(): DatabaseQueryResponse {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams FROM Members WHERE inProgram = true"
        return queryMembersData(query)
    }

    fun getAllMembers(): DatabaseQueryResponse {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams FROM Members"
        return queryMembersData(query)
    }

    fun addMember(fullname: String, id: String, email: String, teams: List<String>): DatabaseUpdateResponse {
        val query = "INSERT INTO Members (id, fullname, points, email, inProgram, level, teams) VALUES (?, ?, 0, ?, false, '1', ?)"
        return executeUpdate(query, id, fullname, email, SqlTextArray(teams))
    }

    fun getMemberByEmail(email: String): DatabaseQueryResponse {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams FROM Members WHERE email = ?"
        return queryMembersData(query, email)
    }

    fun deleteMember(id: String): DatabaseUpdateResponse {
        val query = "DELETE FROM Members WHERE id = ?"
        return executeUpdate(query, id)
    }

    fun addPoints(id: String, points: Int): DatabaseUpdateResponse{
        val query = "UPDATE Members SET points = points + ?, update_at = NOW() WHERE id = ?"
        return executeUpdate(query, points, id)
    }

    fun resetAllPointsAndLevels(): DatabaseUpdateResponse {
        val query = "UPDATE Members SET points = 0, level = '1', update_at = NOW()"
        return executeUpdate(query)
    }

    fun updateTeam(id: String, teams: List<String>): DatabaseUpdateResponse{
        val query = "UPDATE Members SET teams = ? WHERE id = ?"
        return executeUpdate(query, SqlTextArray(teams), id)
    }

    fun updateInProgram(id: String, inProgram: Boolean): DatabaseUpdateResponse {
        val query = "UPDATE Members SET inProgram = $inProgram, update_at = NOW() WHERE id = ?"
        return executeUpdate(query, id)
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
