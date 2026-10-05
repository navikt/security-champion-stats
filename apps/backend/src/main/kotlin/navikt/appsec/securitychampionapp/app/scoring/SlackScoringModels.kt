package navikt.appsec.securitychampionapp.app.scoring

import java.time.Instant
import java.util.UUID

data class SlackActivityMessage(
    val channelId: String,
    val userId: String?,
    val text: String?,
    val timestamp: Instant,
    val editedAt: Instant? = null,
    val subtype: String?,
    val botId: String?,
)

data class SlackAccountMapping(
    val slackUserId: String,
    val participantId: UUID,
    val participantName: String,
    val participantEmail: String,
    val createdAt: Instant,
)

data class UnmappedSlackAuthor(
    val slackUserId: String,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)

data class MappedSlackParticipant(
    val slackUserId: String,
    val participantId: UUID,
    val active: Boolean,
    val enrolledAt: Instant,
)

data class SlackMappingOverview(
    val mappings: List<SlackAccountMapping>,
    val unmappedAuthors: List<UnmappedSlackAuthor>,
)

data class AddSlackAccountMappingRequest(
    val slackUserId: String,
    val participantId: String,
)

data class SlackSyncSummary(
    val messagesScanned: Int,
    val creditsAwarded: Int,
    val duplicateCredits: Int,
    val unmappedAuthors: Int,
)
