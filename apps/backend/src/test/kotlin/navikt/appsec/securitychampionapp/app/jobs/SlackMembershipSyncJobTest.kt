package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.membership.SlackMembershipService
import navikt.appsec.securitychampionapp.app.membership.SlackMembershipPreview
import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.config.SlackMembershipProperties
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.SlackMembershipRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SlackMembershipSyncJobTest {
    private val lock = mock<PostgresJobLock>()
    private val trigger = mock<ScoringSyncTrigger>()
    private val service = mock<SlackMembershipService>()
    private val repository = mock<SlackMembershipRepository>()
    private val audit = mock<ProgramAuditService>()

    @Test
    fun `disabled job performs no scheduled or manual sync`() {
        val job = job(SlackMembershipProperties())

        job.scheduledSync()

        assertThat(job.triggerManualSync("admin@nav.no")).isEqualTo(SyncTriggerResult.DISABLED)
        assertThat(job.configuration()).isEqualTo(SlackMembershipConfiguration(false, true))
        verifyNoInteractions(lock, trigger, service, repository, audit)
    }

    @Test
    fun `enabled jobs share the same dedicated lock for scheduled and manual runs`() {
        val job = job(SlackMembershipProperties(enabled = true))
        whenever(trigger.trigger(eq(1_008L), eq("syncSlackMembership"), eq("admin@nav.no"), any()))
            .thenReturn(SyncTriggerResult.STARTED)

        job.scheduledSync()

        verify(lock).runWithLock(eq(1_008L), eq("syncSlackMembership"), any())
        assertThat(job.triggerManualSync("admin@nav.no")).isEqualTo(SyncTriggerResult.STARTED)
    }

    @Test
    fun `scheduled execution honors dry run and records its outcome`() {
        val job = job(SlackMembershipProperties(enabled = true, dryRun = true))
        doAnswer { invocation -> invocation.getArgument<() -> Unit>(2).invoke() }
            .whenever(lock).runWithLock(any(), any(), any())
        whenever(service.sync(true)).thenReturn(SlackMembershipPreview(emptySet(), emptySet(), emptySet(), 128))
        whenever(repository.announcements(any())).thenReturn(emptyList())

        job.scheduledSync()

        verify(service).sync(true)
        verify(audit).recordRun(eq("SLACK_MEMBERSHIP_SYNC_COMPLETED"), eq(AuditOutcome.SUCCEEDED), any(), any())
    }

    @Test
    fun `zero-participant failure is recorded and propagated`() {
        val job = job(SlackMembershipProperties(enabled = true))
        doAnswer { invocation -> invocation.getArgument<() -> Unit>(2).invoke() }
            .whenever(lock).runWithLock(any(), any(), any())
        whenever(service.sync(true)).thenThrow(IllegalStateException("zero active participants"))

        org.assertj.core.api.Assertions.assertThatThrownBy { job.scheduledSync() }
            .hasMessageContaining("zero active participants")

        verify(audit).recordRun(eq("SLACK_MEMBERSHIP_SYNC_FAILED"), eq(AuditOutcome.FAILED), any(), any())
    }

    private fun job(properties: SlackMembershipProperties) =
        SlackMembershipSyncJob(lock, trigger, service, repository, properties, audit)
}
