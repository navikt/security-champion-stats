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
        val lease = tryAcquireLock(lockKey, jobName) ?: return
        lease.use { block() }
    }

    fun tryAcquireLock(lockKey: Long, jobName: String): LockLease? {
        val connection = dataSource.connection
        return try {
            if (!tryAcquireLock(connection, lockKey)) {
                connection.close()
                log.info("Skipping $jobName because another instance already holds the lock")
                null
            } else {
                LockLease(connection, lockKey, jobName)
            }
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }

    inner class LockLease internal constructor(
        private val connection: Connection,
        private val lockKey: Long,
        private val jobName: String,
    ) : AutoCloseable {
        override fun close() {
            try {
                if (!releaseLock(connection, lockKey)) {
                    log.warn("Failed to release advisory lock for $jobName")
                }
            } finally {
                connection.close()
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
