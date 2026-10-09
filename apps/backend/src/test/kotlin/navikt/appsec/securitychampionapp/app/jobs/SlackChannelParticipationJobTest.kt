package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.membership.ChannelCheckStatus
import navikt.appsec.securitychampionapp.app.membership.SlackChannelParticipationSettings
import navikt.appsec.securitychampionapp.config.SlackChannelParticipationProperties
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.SlackChannelParticipationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Instant

class SlackChannelParticipationJobTest {
    private val lock = mock<PostgresJobLock>()
    private val repository = mock<SlackChannelParticipationRepository>()
    private val job = SlackChannelParticipationJob(
        lock, mock(), mock(), repository, SlackChannelParticipationProperties(enabled = true),
        SlackChannelParticipationSettings("C_CHANNEL", 5), mock(), Clock.systemUTC(),
    )

    @Test
    fun `a running status without a held lock is reported as an interrupted failure`() {
        whenever(repository.status("C_CHANNEL"))
            .thenReturn(ChannelCheckStatus(Instant.EPOCH, null, "RUNNING", null))
        whenever(lock.isLocked(1_011L)).thenReturn(false)

        val overview = job.overview()

        assertThat(overview.outcome).isEqualTo("FAILED")
        assertThat(overview.failureSummary).isEqualTo("Slack channel check was interrupted")
    }

    @Test
    fun `a running status with a held lock remains running`() {
        whenever(repository.status("C_CHANNEL"))
            .thenReturn(ChannelCheckStatus(Instant.EPOCH, null, "RUNNING", null))
        whenever(lock.isLocked(1_011L)).thenReturn(true)

        assertThat(job.overview().outcome).isEqualTo("RUNNING")
    }
}
