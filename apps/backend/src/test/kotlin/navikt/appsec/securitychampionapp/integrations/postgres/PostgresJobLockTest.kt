package navikt.appsec.securitychampionapp.integrations.postgres

import navikt.appsec.securitychampionapp.testsupport.PostgresIntegrationTest
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class PostgresJobLockTest : PostgresIntegrationTest(maxPoolSize = 10) {
    @Test
    fun `should skip a sequential scheduled duplicate until its interval expires`() {
        val jobLock = PostgresJobLock(dataSource)
        var executions = 0

        jobLock.runWithLockAtMostOncePerInterval(1_006L, "importPlaybookEvents", Duration.ofHours(6)) {
            executions++
        }
        jobLock.runWithLockAtMostOncePerInterval(1_006L, "importPlaybookEvents", Duration.ofHours(6)) {
            executions++
        }

        assertThat(executions).isEqualTo(1)

        jdbcTemplate.update(
            "UPDATE scheduled_job_runs SET last_started_at = last_started_at - INTERVAL '6 hours 1 second'",
        )

        jobLock.runWithLockAtMostOncePerInterval(1_006L, "importPlaybookEvents", Duration.ofHours(6)) {
            executions++
        }

        assertThat(executions).isEqualTo(2)
    }
}
