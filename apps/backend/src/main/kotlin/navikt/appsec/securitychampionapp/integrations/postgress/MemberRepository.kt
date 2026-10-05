package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.api.dto.SCdata
import navikt.appsec.securitychampionapp.integrations.postgress.dto.MemberQueryResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.MemberUpdateResponse
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlMember
import navikt.appsec.securitychampionapp.integrations.postgress.dto.SqlTextArray
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant


@Repository
class MemberRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private fun queryMembersData(query: String, vararg args: Any): MemberQueryResponse {
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
                    teams = teams,
                    createdAt = rs.getString("create_at")
                )
            }
            if (args.isEmpty()) {
                MemberQueryResponse(
                    isOk = true,
                    jdbcTemplate.query(query, rowMapper)
                )
            } else {
                MemberQueryResponse(
                    isOk = true,
                    jdbcTemplate.query(query, rowMapper, *args)

                )
            }
        } catch (e: Exception) {
            MemberQueryResponse(
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

    private fun executeUpdate(query: String, vararg args: Any): MemberUpdateResponse {
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
            return MemberUpdateResponse(isOk = true)
        } catch (e: Exception) {
            return MemberUpdateResponse(
                isOk = false,
                error = "Failed to execute update: ${e.message}"
            )
        }
    }

    fun getAllMembersInProgram(): MemberQueryResponse {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams FROM Members WHERE inProgram = true"
        return queryMembersData(query)
    }

    fun getAllMembers(): MemberQueryResponse {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, create_at, teams FROM Members"
        return queryMembersData(query)
    }

    fun addMember(fullname: String, id: String, email: String, teams: List<String>): MemberUpdateResponse {
        val query = "INSERT INTO Members (id, fullname, points, email, inProgram, level, teams, create_at) VALUES (?, ?, 0, ?, false, '1', ?, CURRENT_TIMESTAMP)"
        return executeUpdate(query, id, fullname, email, SqlTextArray(teams))
    }

    fun getMemberByEmail(email: String): MemberQueryResponse {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, create_at, teams FROM Members WHERE email = ?"
        return queryMembersData(query, email)
    }

    fun deleteMember(id: String): MemberUpdateResponse {
        val query = "DELETE FROM Members WHERE id = ?"
        return executeUpdate(query, id)
    }
    fun updateTeam(id: String, teams: List<String>): MemberUpdateResponse{
        val query = "UPDATE Members SET teams = ? WHERE id = ?"
        return executeUpdate(query, SqlTextArray(teams), id)
    }

    fun updateInProgram(id: String, inProgram: Boolean): MemberUpdateResponse {
        val query = "UPDATE Members SET inProgram = ?, update_at = NOW() WHERE id = ?"
        return executeUpdate(query, inProgram, id)
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

    fun fetchMember(id: String): MemberQueryResponse? {
        val query = "SELECT id, fullname, points, email, update_at, inProgram, level, teams, create_at FROM Members WHERE id = ?"
        return queryMembersData(query, id)
    }
}
