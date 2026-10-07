package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.github.GitHubContributionSource
import navikt.appsec.securitychampionapp.integrations.postgress.GitHubIdentityMappingRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class GitHubSyncSummary(
    val contributionsScanned: Int,
    val creditsAwarded: Int,
    val duplicateCredits: Int,
    val unmappedAuthors: Int,
)

@Service
class GitHubScoringService(
    private val source: GitHubContributionSource,
    private val mappings: GitHubIdentityMappingRepository,
    private val repository: ScoringLedger,
    private val scoringService: ScoringService,
) {
    fun sync(now: Instant, correlationId: UUID? = null): GitHubSyncSummary {
        val season = repository.currentSeason()
        val startsAt = season.startsOn.atStartOfDay(ZoneId.of("Europe/Oslo")).toInstant()
        val identities = source.identities()
        val contributions = source.contributions(startsAt, now)
        val participants = mappings.refresh(identities, now)
        val unmapped = mutableSetOf<Long>()
        var awarded = 0
        var duplicates = 0
        contributions.forEach { contribution ->
            if (contribution.occurredAt.isBefore(startsAt) || contribution.occurredAt.isAfter(now)) return@forEach
            val participant = participants[contribution.accountId]
            if (participant == null) {
                unmapped += contribution.accountId
                return@forEach
            }
            if (!participant.active || !contribution.occurredAt.isAfter(participant.enrolledAt)) return@forEach
            when (
                scoringService.awardGitHubCredit(
                    participantId = participant.participantId,
                    creditType = contribution.type,
                    uniquenessKey = contribution.key,
                    sourceReference = contribution.key,
                    auditCorrelationId = correlationId,
                    activityAt = contribution.occurredAt,
                    expectedSeasonId = season.id,
                )
            ) {
                CreditAwardResult.AWARDED -> awarded++
                CreditAwardResult.DUPLICATE -> duplicates++
                CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING -> Unit
            }
        }
        return GitHubSyncSummary(contributions.size, awarded, duplicates, unmapped.size)
    }
}
