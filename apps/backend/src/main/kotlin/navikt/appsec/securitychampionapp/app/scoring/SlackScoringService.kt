package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.postgress.SlackIdentityMappingRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackApiService
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val SLACK_FETCH_LOOKBACK: Duration = Duration.ofDays(90)
private val NAV_NO_EMAIL = Regex("^[A-Za-z0-9+_.-]+@nav\\.no$", RegexOption.IGNORE_CASE)
private const val EMAIL_MATCH_ACTOR = "system:slack-email-match"

@Service
class SlackScoringService(
    private val slackApiService: SlackApiService,
    private val mappingRepository: SlackIdentityMappingRepository,
    private val scoringService: ScoringService,
) {
    fun mappingOverview(): SlackMappingOverview {
        val (mappings, unmappedAuthors) = mappingRepository.mappingOverview()
        return SlackMappingOverview(mappings, unmappedAuthors)
    }

    fun addMapping(
        slackUserId: String,
        participantId: UUID,
        actorNavNoEmail: String,
    ): Boolean {
        val normalizedSlackUserId = slackUserId.trim()
        if (normalizedSlackUserId.isEmpty() || normalizedSlackUserId.length > 100) {
            throw InvalidScoringRequestException("A valid Slack user ID is required")
        }
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        return mappingRepository.addMapping(normalizedSlackUserId, participantId, actorNavNoEmail)
    }

    fun removeMapping(slackUserId: String, actorNavNoEmail: String): Boolean {
        if (slackUserId.isBlank()) throw InvalidScoringRequestException("A Slack user ID is required")
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        return mappingRepository.removeMapping(slackUserId, actorNavNoEmail)
    }

    fun sync(channelId: String, now: Instant, auditCorrelationId: UUID? = null): SlackSyncSummary {
        if (channelId.isBlank()) throw IllegalStateException("Slack scoring channel is not configured")
        val cursor = mappingRepository.syncCursor(channelId, now)
        val messages = slackApiService.fetchScoringMessages(channelId, cursor.minus(SLACK_FETCH_LOOKBACK), now)
            .sortedBy { it.timestamp }
        val mappedParticipants = autoMapAuthors(messages, mappingRepository.mappedParticipants())
        val unmappedAuthors = mutableSetOf<String>()
        var creditsAwarded = 0
        var duplicateCredits = 0

        messages.forEach { message ->
            val userId = message.userId?.takeIf { it.isNotBlank() } ?: return@forEach
            if (message.botId != null || !message.isAuthoredMessage()) return@forEach
            val activityAt = message.editedAt?.takeIf { it.isAfter(message.timestamp) } ?: message.timestamp
            if (activityAt.isBefore(cursor) || activityAt.isAfter(now)) return@forEach

            val participant = mappedParticipants[userId]
            if (participant == null) {
                mappingRepository.recordUnmappedAuthor(userId)
                unmappedAuthors += userId
                return@forEach
            }
            if (!participant.active ||
                !message.timestamp.isAfter(participant.enrolledAt) ||
                !SlackScoringRules.qualifies(message.text.orEmpty())
            ) {
                return@forEach
            }

            val weekStart = SlackScoringRules.weekStart(message.timestamp)
            when (
                scoringService.awardCredit(
                    participantId = participant.participantId,
                    creditType = ActivityCreditType.SLACK_WEEK,
                    uniquenessKey = weekStart.toString(),
                    sourceReference = "${message.channelId}:${message.timestamp}",
                    auditCorrelationId = auditCorrelationId,
                )
            ) {
                CreditAwardResult.AWARDED -> creditsAwarded++
                CreditAwardResult.DUPLICATE -> duplicateCredits++
                CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING -> Unit
            }
        }
        mappingRepository.advanceSyncCursor(channelId, now)
        return SlackSyncSummary(
            messagesScanned = messages.size,
            creditsAwarded = creditsAwarded,
            duplicateCredits = duplicateCredits,
            unmappedAuthors = unmappedAuthors.size,
        )
    }

    private fun autoMapAuthors(
        messages: List<SlackActivityMessage>,
        mapped: Map<String, MappedSlackParticipant>,
    ): Map<String, MappedSlackParticipant> {
        val newlyMapped = messages.asSequence()
            .filter { it.botId == null && it.isAuthoredMessage() }
            .mapNotNull { it.userId?.takeIf(String::isNotBlank) }
            .distinct()
            .filterNot(mapped::containsKey)
            .count { userId ->
                val email = slackApiService.fetchUserEmail(userId)?.trim()
                email != null && NAV_NO_EMAIL.matches(email) &&
                    mappingRepository.addMappingByNavNoEmail(userId, email, EMAIL_MATCH_ACTOR)
            }
        return if (newlyMapped > 0) mappingRepository.mappedParticipants() else mapped
    }

    private fun SlackActivityMessage.isAuthoredMessage(): Boolean =
        subtype == null || subtype in AUTHORED_MESSAGE_SUBTYPES

    private companion object {
        val AUTHORED_MESSAGE_SUBTYPES = setOf(
            "file_share",
            "me_message",
            "message_changed",
            "thread_broadcast",
        )
    }
}
