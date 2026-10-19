package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.MemberWithTeamData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import java.util.UUID

class SyncJobTest {
    private val jobLock = Mockito.mock(PostgresJobLock::class.java)
    private val catalog = mock<TeamCatalog>()
    private val participants = mock<ParticipantStore>()
    private val audit = mock<ProgramAuditService>()

    @BeforeEach
    fun runJobInsideLock() {
        doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(2).invoke()
            null
        }.whenever(jobLock).runWithLock(any(), any(), any())
    }

    private fun syncJob() = SyncJob(jobLock, participants, catalog, audit)

    @Test
    fun `should update profile data for enrolled participants`() {
        whenever(participants.findAllParticipants()).thenReturn(listOf(participant("user@nav.no", "A12345")))
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(listOf(member("A12345", "user@nav.no", "Test User")))
        whenever(participants.updateProfile("A12345", "user@nav.no", "Test User", listOf("Team"))).thenReturn(1)

        syncJob().syncDatabase()

        verify(participants).updateProfile("A12345", "user@nav.no", "Test User", listOf("Team"))
        verify(audit).recordRun(
            eq("PARTICIPANT_PROFILE_SYNC_COMPLETED"),
            eq(AuditOutcome.SUCCEEDED),
            any<AuditRunContext>(),
            eq(mapOf("profilesUpdated" to 1, "failedProfiles" to 0)),
        )
    }

    @Test
    fun `should not touch Teamkatalogen members that are not enrolled or whose email differs`() {
        whenever(participants.findAllParticipants()).thenReturn(listOf(participant("user@nav.no", "A12345")))
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(
            listOf(
                member("A99999", "not-enrolled@nav.no", "Not Enrolled"),
                member("A12345", "other@nav.no", "Other Email"),
            ),
        )

        syncJob().syncDatabase()

        verify(participants, never()).updateProfile(any(), any(), any(), any())
    }

    @Test
    fun `should retain participants absent from Teamkatalogen and record the failed run`() {
        whenever(participants.findAllParticipants()).thenReturn(listOf(participant("user@nav.no", "A12345")))
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(emptyList())

        syncJob().syncDatabase()

        verify(participants, never()).updateProfile(any(), any(), any(), any())
        verify(audit).recordRun(
            eq("PARTICIPANT_PROFILE_SYNC_FAILED"),
            eq(AuditOutcome.FAILED),
            any<AuditRunContext>(),
            eq(mapOf("failure" to "emptyCatalog")),
        )
    }

    @Test
    fun `should not sync when another instance holds the lock`() {
        doAnswer { null }.whenever(jobLock).runWithLock(any(), any(), any())

        syncJob().syncDatabase()

        verify(jobLock).runWithLock(any(), any(), any())
        verify(catalog, never()).fetchAllMembersWithTeamData()
    }

    @Test
    fun `should continue profile sync and record partial completion after a persistence failure`() {
        whenever(participants.findAllParticipants()).thenReturn(
            listOf(participant("first@nav.no", "A11111"), participant("second@nav.no", "A22222")),
        )
        whenever(catalog.fetchAllMembersWithTeamData()).thenReturn(
            listOf(member("A11111", "first@nav.no", "First User"), member("A22222", "second@nav.no", "Second User")),
        )
        whenever(participants.updateProfile("A11111", "first@nav.no", "First User", listOf("Team")))
            .thenThrow(DataAccessResourceFailureException("Synthetic database failure"))
        whenever(participants.updateProfile("A22222", "second@nav.no", "Second User", listOf("Team"))).thenReturn(1)

        syncJob().syncDatabase()

        verify(participants).updateProfile("A11111", "first@nav.no", "First User", listOf("Team"))
        verify(participants).updateProfile("A22222", "second@nav.no", "Second User", listOf("Team"))
        verify(audit).recordRun(
            eq("PARTICIPANT_PROFILE_SYNC_COMPLETED"),
            eq(AuditOutcome.PARTIAL),
            any<AuditRunContext>(),
            eq(mapOf("profilesUpdated" to 1, "failedProfiles" to 1)),
        )
    }

    private fun member(navIdent: String, email: String, fullName: String) = MemberWithTeamData(
        navIdent = navIdent,
        fullName = fullName,
        email = email,
        teamName = mutableListOf("Team"),
        teamId = mutableListOf("team-id"),
    )

    private fun participant(email: String, navIdent: String) = ProgramParticipant(
        id = UUID.randomUUID(),
        navNoEmail = email,
        navIdent = navIdent,
        email = email,
        fullname = email,
        teams = emptyList(),
        status = ParticipationStatus.ACTIVE,
        createdAt = "2026-10-07T00:00:00Z",
    )
}
