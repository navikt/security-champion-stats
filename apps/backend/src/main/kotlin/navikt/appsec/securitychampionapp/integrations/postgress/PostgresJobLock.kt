package navikt.appsec.securitychampionapp.integrations.postgress

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

@Component
class PostgresJobLock(
    private val dataSource: DataSource,
) {
    private val log = LoggerFactory.getLogger(PostgresJobLock::class.java)

    fun runWithLock(lockKey: Long, jobName: String, block: () -> Unit) {
        dataSource.connection.use { connection ->
            if (!tryAcquireLock(connection, lockKey)) {
                log.info("Skipping $jobName because another instance already holds the lock")
                return
            }

            try {
                block()
            } finally {
                if (!releaseLock(connection, lockKey)) {
                    log.warn("Failed to release advisory lock for $jobName")
                }
            }
        }
    }

    private fun tryAcquireLock(connection: Connection, lockKey: Long): Boolean {
        connection.prepareStatement("SELECT pg_try_advisory_lock(?)").use { statement ->
            statement.setLong(1, lockKey)
            statement.executeQuery().use { resultSet ->
                return resultSet.next() && resultSet.getBoolean(1)
            }
        }
    }

    private fun releaseLock(connection: Connection, lockKey: Long): Boolean {
        connection.prepareStatement("SELECT pg_advisory_unlock(?)").use { statement ->
            statement.setLong(1, lockKey)
            statement.executeQuery().use { resultSet ->
                return resultSet.next() && resultSet.getBoolean(1)
            }
        }
    }
}

