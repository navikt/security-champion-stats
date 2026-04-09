package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.common.slack.SlackService
import navikt.appsec.securitychampionstats.common.slack.dto.SlackActivitySummary
import navikt.appsec.securitychampionstats.common.slack.dto.UserInfo
import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.common.teamCatalog.dto.ResourceResponse
import navikt.appsec.securitychampionstats.stats.dto.Member
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


class SyncJobTest {

    private val simpleMemberList = listOf(
        Member(
            id = "test-member-1",
            fullname = "Test User",
            points = 0,
            lastUpdated = null,
            email = "test@nav.no",
            inProgram = true
        ),
        Member(
            id = "test-member-2",
            fullname = "Test User2",
            points = 0,
            lastUpdated = "2024-01-01T00:00:00Z",
            email = "test2@nav.no",
            inProgram = false
        )
    )

    private val catalogResponse = listOf(
        ResourceResponse(
            navIdent = "TestID1",
            fullName = "Test User3",
            email = "test3@nav.no"
        ),
        ResourceResponse(
            navIdent = "TestID2",
            fullName = "Test User4",
            email = "test2@nav.no"
        )
    )

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(
            SyncJob::class.java,
            TestConfig::class.java,
        )
        .withPropertyValues(
            "points.message=10",
            "slack.sc-channel-id=sc-channel-id",
            "slack.appsec-channel-id=appsec-channel-id"
        )

    @Configuration
    class TestConfig {
        @Bean
        fun repo(): PostgresRepository = Mockito.mock(PostgresRepository::class.java)
        @Bean
        fun catalog(): TeamCatalog = Mockito.mock(TeamCatalog::class.java)
        @Bean
        fun slackService(): SlackService = Mockito.mock(SlackService::class.java)
    }

    @Test
    fun `SyncJob shouldn't fail by fetching user with null in lastUpdated and dont add duplicate members`() {
        contextRunner.run { context ->
            val sync = context.getBean<SyncJob>()
            val catalog = context.getBean<TeamCatalog>()
            val slackService = context.getBean<SlackService>()
            val repo = context.getBean<PostgresRepository>()

            whenever(repo.getAllMembers()).thenReturn(simpleMemberList)
            whenever(catalog.fetchMembersWithRole()).thenReturn(catalogResponse)
            doNothing().whenever(repo).addMember(any(), any(), any())
            whenever(repo.getAllMembersInProgram()).thenReturn(simpleMemberList.filter { it.inProgram })
            whenever(slackService.getUserActivitySummaryByEmail(any(), any())).thenReturn(
                SlackActivitySummary(
                    userInfo = UserInfo("id", "Test User"),
                    inTrackedChannels = listOf("sc-channel-id", "appsec-channel-id"),
                    messagesPerChannel = mapOf(
                        "sc-channel-id" to 5,
                        "appsec-channel-id" to 3
                    ),
                    totalMessages = 8
                )
            )

            sync.syncDatabase()
            verify(repo).addMember(any(),any(),any())
            verify(repo).addPoints(any(), any())
        }
    }
}