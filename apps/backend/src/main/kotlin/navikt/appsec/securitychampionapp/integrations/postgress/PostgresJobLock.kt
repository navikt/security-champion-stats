package navikt.appsec.securitychampionapp.integrations.postgress

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection
import java.time.Duration
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

    fun runWithLockAtMostOncePerInterval(
        lockKey: Long,
        jobName: String,
        interval: Duration,
        block: () -> Unit,
    ) {
        require(!interval.isNegative && !interval.isZero) { "interval must be positive" }
        val lease = tryAcquireLock(lockKey, jobName) ?: return
        lease.use {
            if (claimRun(lease.connection, jobName, interval)) {
                block()
            } else {
                log.info("Skipping $jobName because it already ran within the configured interval")
            }
        }
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

    fun isLocked(lockKey: Long): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    SELECT EXISTS (
                        SELECT 1 FROM pg_locks
                        WHERE locktype = 'advisory'
                            AND granted
                            AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
                            AND classid = ((? >> 32) & 4294967295)::oid
                            AND objid = (? & 4294967295)::oid
                            AND objsubid = 1
                    )
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, lockKey)
                statement.setLong(2, lockKey)
                statement.executeQuery().use { resultSet -> resultSet.next() && resultSet.getBoolean(1) }
            }
        }

    inner class LockLease internal constructor(
        internal val connection: Connection,
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

    private fun claimRun(connection: Connection, jobName: String, interval: Duration): Boolean =
        connection.prepareStatement(
            """
                INSERT INTO scheduled_job_runs (job_name, last_started_at)
                VALUES (?, clock_timestamp())
                ON CONFLICT (job_name) DO UPDATE
                SET last_started_at = EXCLUDED.last_started_at
                WHERE scheduled_job_runs.last_started_at
                    <= EXCLUDED.last_started_at - (? * INTERVAL '1 millisecond')
                RETURNING job_name
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, jobName)
            statement.setLong(2, interval.toMillis())
            statement.executeQuery().use { resultSet -> resultSet.next() }
        }
}
