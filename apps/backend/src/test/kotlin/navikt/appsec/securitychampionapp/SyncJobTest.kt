package navikt.appsec.securitychampionapp

import navikt.appsec.securitychampionapp.app.SyncJob
import navikt.appsec.securitychampionapp.app.api.dto.Member
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackService
import navikt.appsec.securitychampionapp.integrations.slack.dto.NewSecurityChampion
import navikt.appsec.securitychampionapp.integrations.slack.dto.RemovedSecurityChampion
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackActivitySummary
import navikt.appsec.securitychampionapp.integrations.slack.dto.UserInfo
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.MemberWithTeamData
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit

class SyncJobTest {

    private val repo = mock(PostgresRepository::class.java)
    private val catalog = mock(TeamCatalog::class.java)
    private val slackService = mock(SlackService::class.java)

    private fun syncJob(activityPoints: String = "10") = SyncJob(
        repo = repo,
        catalog = catalog,
        slackService = slackService,
        activityPoints = activityPoints,
        scChannelId = "sc-channel-id",
        appSecId = "appsec-channel-id",
    )

    @Test
    fun `should add new members and remove members no longer in team catalog`() {
        val existingMember = member(
            id = "existing-id",
            fullname = "Existing Member",
            email = "existing@nav.no",
        )
        val removedMember = member(
            id = "removed-id",
            fullname = "Removed Member",
            email = "removed@nav.no",
        )

        whenever(repo.getAllMembers()).thenReturn(listOf(existingMember, removedMember))
        whenever(catalog.fetchMembersWithRole()).thenReturn(
            listOf(
                catalogMember(
                    navIdent = "existing-id",
                    fullName = "Existing Member",
                    email = "existing@nav.no",
                    teamNames = mutableListOf("Existing Team"),
                ),
                catalogMember(
                    navIdent = "new-id",
                    fullName = "New Member",
                    email = "new@nav.no",
                    teamNames = mutableListOf("New Team"),
                ),
            )
        )
        whenever(repo.getAllMembersInProgram()).thenReturn(emptyList())

        syncJob().syncDatabase()

        verify(repo).addMember("New Member", "new-id", "new@nav.no", emptyList())
        verify(slackService).addSecurityChampionsToSlack(
            champions = listOf(
                NewSecurityChampion(
                    email = "new@nav.no",
                    teamNames = listOf("New Team"),
                    fullName = "New Member",
                )
            )
        )
        verify(repo).deleteMember("removed@nav.no")
        verify(slackService).announceSecurityChampionsRemovedFromSlack(
            champions = listOf(
                RemovedSecurityChampion(
                    email = "removed@nav.no",
                    teamName = "",
                    fullName = "Removed Member",
                )
            )
        )
    }

    @Test
    fun `should add points when an in-program member has no lastUpdated value and slack activity`() {
        val member = member(
            id = "member-id",
            fullname = "Security Champion",
            points = 20,
            lastUpdated = null,
            email = "champion@nav.no",
            inProgram = true,
        )

        whenever(repo.getAllMembers()).thenReturn(emptyList())
        whenever(catalog.fetchMembersWithRole()).thenReturn(emptyList())
        whenever(repo.getAllMembersInProgram()).thenReturn(listOf(member))
        whenever(
            slackService.getUserActivitySummaryByEmail(
                "champion@nav.no",
                listOf("sc-channel-id", "appsec-channel-id"),
            )
        ).thenReturn(activitySummary(totalMessages = 3))

        syncJob().syncDatabase()

        verify(repo).addPoints("champion@nav.no", 50)
    }

    @Test
    fun `should skip point updates for recently updated members and members without slack activity`() {
        val recentlyUpdatedMember = member(
            id = "recent-id",
            fullname = "Recent Member",
            lastUpdated = Instant.now().minus(1, ChronoUnit.DAYS).toString(),
            email = "recent@nav.no",
            inProgram = true,
        )
        val inactiveMember = member(
            id = "inactive-id",
            fullname = "Inactive Member",
            lastUpdated = Instant.now().minus(3, ChronoUnit.DAYS).toString(),
            email = "inactive@nav.no",
            inProgram = true,
        )

        whenever(repo.getAllMembers()).thenReturn(emptyList())
        whenever(catalog.fetchMembersWithRole()).thenReturn(emptyList())
        whenever(repo.getAllMembersInProgram()).thenReturn(listOf(recentlyUpdatedMember, inactiveMember))
        whenever(
            slackService.getUserActivitySummaryByEmail(
                "inactive@nav.no",
                listOf("sc-channel-id", "appsec-channel-id"),
            )
        ).thenReturn(activitySummary(totalMessages = 0))

        syncJob().syncDatabase()

        verify(slackService, never()).getUserActivitySummaryByEmail(
            "recent@nav.no",
            listOf("sc-channel-id", "appsec-channel-id"),
        )
        verify(repo, never()).addPoints(any(), any())
    }

    private fun member(
        id: String,
        fullname: String,
        points: Int = 0,
        lastUpdated: String? = null,
        email: String,
        inProgram: Boolean = false,
    ) = Member(
        id = id,
        fullname = fullname,
        points = points,
        lastUpdated = lastUpdated,
        email = email,
        inProgram = inProgram,
    )

    private fun catalogMember(
        navIdent: String,
        fullName: String,
        email: String,
        teamNames: MutableList<String>,
    ) = MemberWithTeamData(
        navIdent = navIdent,
        fullName = fullName,
        email = email,
        teamName = teamNames,
        teamId = mutableListOf("team-id"),
    )

    private fun activitySummary(totalMessages: Int) = SlackActivitySummary(
        userInfo = UserInfo(userId = "user-id", fullname = "Security Champion"),
        inTrackedChannels = listOf("sc-channel-id", "appsec-channel-id"),
        messagesPerChannel = mapOf("sc-channel-id" to totalMessages),
        totalMessages = totalMessages,
    )
}