package navikt.appsec.securitychampionstats.integration.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import navikt.appsec.securitychampionstats.server.dto.MemberDTO
import org.slf4j.LoggerFactory

object PostgresRepository {
    private val logger = LoggerFactory.getLogger(LoggerFactory::class.java)
    private val dbUsername = System.getenv("NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_USERNAME")
    private val dbPassword = System.getenv("NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_PASSWORD")
    private val jdcUrl = System.getenv("NAIS_DATABASE_SECURITY_CHAMPION_STATS_BACKEND_SECURITY_CHAMPION_STATS_DB_JDBC_URL")

    private val dataSource: HikariDataSource by lazy {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = jdcUrl
            username = dbUsername
            password = dbPassword
            driverClassName = "org.postgresql.Driver"

            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 5_000
            validationTimeout = 3_000
            leakDetectionThreshold = 10_000
        }
        HikariDataSource(hikariConfig)
    }
    //TODO: Create source table for sql (tips look at teamkatalog)
    private fun checkIfTableExist(): Boolean {
        val sqlStatement = "SELECT COUNT(*) = 0 AS no_tables FROM information_schema.tables WHERE table_schema NOT IN (\'pg_catalog\', \'information_schema\')"
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sqlStatement).use { preparedStatement ->
                    preparedStatement.executeQuery().use { resultSet ->
                        resultSet.next()
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[Postgres]: Failed to check if table exist: ${e.message}")
            false
        }
    }

    fun createTable() {

    }

    fun getMember(fullname: String, email: String): MemberDTO {
        val sqlQuery = "SELECT name FROM Members WHERE fullname = ? AND email = ?"
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sqlQuery).use { preparedStatement ->
                    preparedStatement.setString(1, fullname)
                    preparedStatement.setString(2, email)
                    preparedStatement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            MemberDTO(
                                resultSet.getString("id"),
                                resultSet.getString("fullname"),
                                resultSet.getInt("points"),
                                resultSet.getString("email"),
                            )
                        } else {
                            logger.warn("[Postgres]: No member found with id: $fullname")
                            MemberDTO("-1", "-1", -1, "-1")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[Postgres]: Failed fetch member due to error: ${e.message}")
            MemberDTO("-1", "-1", -1, "-1")
        }
    }

    fun getAllMembers(): List<MemberDTO>{
        if (!checkIfTableExist("Members")) return emptyList()
        val sql = "SELECT name FROM Members"
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { preparedStatement ->
                    preparedStatement.executeQuery().use { resultSet ->
                        buildList{
                            while (resultSet.next()) {
                                val id = resultSet.getString("id")
                                val fullname = resultSet.getString("fullname")
                                val points = resultSet.getInt("points")
                                val email = resultSet.getString("email")
                                add(MemberDTO(id, fullname, points, email))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[Postgres]: Failed to fetch all members due to error: ${e.message}")
            emptyList()
        }
    }

    fun updateMember(fullname: String, email: String, points: Int) {
        val sql = "UPDATE Members SET points = ? WHERE fullname = ? and email = ?"
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { preparedStatement ->
                    preparedStatement.setInt(1, points)
                    preparedStatement.setString(2, fullname)
                    preparedStatement.setString(3, email)
                    preparedStatement.executeQuery()
                }
            }
        } catch (e: Exception) {
            logger.error("[Postgres]: Failed to update member due to error: ${e.message}")
        }
    }

    fun addMember(fullname: String, id: String) {
        val sql = "INSERT INTO Members (id, name) VALUES (?, ?)"
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { preparedStatement ->
                    preparedStatement.setString(1, id)
                    preparedStatement.setString(2, fullname)
                    preparedStatement.executeUpdate()
                }
            }
            logger.info("[Postgres]: Successfully added member with id: $id")
        } catch (e: Exception) {
            logger.error("[Postgres]: Failed to add member due to error: ${e.message}")
        }
    }

    fun getPointsForMember(id: String) {/*TODO implement */}
    fun addPointsForMember(id: String, points: Int) {/*TODO implement */}
    fun deletePointsForMember(id: String) {/*TODO: Implement*/}
    fun deleteMember(id: String) {/*TODO: Implement*/}
}