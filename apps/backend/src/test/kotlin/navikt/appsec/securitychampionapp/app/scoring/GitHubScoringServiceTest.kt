package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.github.GitHubContribution
import navikt.appsec.securitychampionapp.integrations.github.GitHubContributionSource
import navikt.appsec.securitychampionapp.integrations.github.GitHubFailure
import navikt.appsec.securitychampionapp.integrations.github.GitHubIdentity
import navikt.appsec.securitychampionapp.integrations.github.GitHubIntegrationException
import navikt.appsec.securitychampionapp.integrations.postgress.GitHubIdentityMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.MappedGitHubParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.ScoringRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GitHubScoringServiceTest {
    private val source = mock<GitHubContributionSource>()
    private val mappings = mock<GitHubIdentityMappingRepository>()
    private val repository = mock<ScoringRepository>()
    private val scoring = mock<ScoringService>()
    private val service = GitHubScoringService(source, mappings, repository, scoring)
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private val startsAt = Instant.parse("2025-12-31T23:00:00Z")
    private val enrolledAt = now.minusSeconds(3600)
    private val seasonId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val identities = listOf(GitHubIdentity(10, "person", "person@nav.no"))

    @Test
    fun `should use Oslo season boundaries post enrollment and active status while preserving idempotent results`() {
        setup()
        val pr = contribution(10, "pr:1", now.minusSeconds(30))
        val commit = contribution(10, "commit:a", now.minusSeconds(60), ActivityCreditType.GITHUB_COMMIT)
        whenever(source.contributions(startsAt, now)).thenReturn(
            listOf(
                pr, commit,
                contribution(10, "before-enrollment", enrolledAt.minusSeconds(1)),
                contribution(10, "at-enrollment", enrolledAt),
                contribution(10, "old-season", startsAt.minusSeconds(1)),
                contribution(10, "future", now.plusSeconds(1)),
                contribution(11, "inactive", now.minusSeconds(1)),
                contribution(12, "unmapped-1", now.minusSeconds(1)),
                contribution(12, "unmapped-2", now.minusSeconds(1)),
            ),
        )
        whenever(
            scoring.awardGitHubCredit(
                eq(participantId), any(), any(), any(), anyOrNull(), any(), eq(seasonId),
            ),
        ).thenReturn(CreditAwardResult.AWARDED, CreditAwardResult.DUPLICATE)

        val summary = service.sync(now)

        assertThat(summary).isEqualTo(GitHubSyncSummary(9, 1, 1, 1))
        verify(scoring).awardGitHubCredit(
            participantId, pr.type, pr.key, pr.key, null, pr.occurredAt, seasonId,
        )
        verify(scoring).awardGitHubCredit(
            participantId, commit.type, commit.key, commit.key, null, commit.occurredAt, seasonId,
        )
        org.mockito.kotlin.verifyNoMoreInteractions(scoring)
    }

    @Test
    fun `should not fetch contributions change mappings or award points when SAML lookup fails`() {
        setup()
        whenever(source.identities()).thenThrow(GitHubIntegrationException(GitHubFailure.IDENTITY))
        assertThatThrownBy { service.sync(now) }.hasMessage(GitHubFailure.IDENTITY.summary)
        verifyNoInteractions(mappings, scoring)
    }

    @Test
    fun `should rescan the same season so later mappings can recover eligible credits`() {
        setup()
        whenever(source.contributions(startsAt, now)).thenReturn(listOf(contribution(10, "pr:1", now)))
        whenever(mappings.refresh(identities, now)).thenReturn(
            emptyMap(), mapOf(10L to MappedGitHubParticipant(10, participantId, true, enrolledAt)),
        )
        whenever(
            scoring.awardGitHubCredit(eq(participantId), any(), any(), any(), anyOrNull(), eq(now), eq(seasonId)),
        ).thenReturn(CreditAwardResult.AWARDED)
        assertThat(service.sync(now).unmappedAuthors).isEqualTo(1)
        assertThat(service.sync(now).creditsAwarded).isEqualTo(1)
    }

    private fun setup() {
        whenever(repository.currentSeason()).thenReturn(
            SeasonSummary(seasonId, LocalDate.parse("2026-01-01"), null, LocalDate.parse("2027-01-01")),
        )
        whenever(source.identities()).thenReturn(identities)
        whenever(mappings.refresh(identities, now)).thenReturn(
            mapOf(
                10L to MappedGitHubParticipant(10, participantId, true, enrolledAt),
                11L to MappedGitHubParticipant(11, UUID.randomUUID(), false, enrolledAt),
            ),
        )
    }

    private fun contribution(
        accountId: Long,
        key: String,
        at: Instant,
        type: ActivityCreditType = ActivityCreditType.GITHUB_PULL_REQUEST,
    ) = GitHubContribution(accountId, type, key, at)
}
