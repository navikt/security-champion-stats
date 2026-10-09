package navikt.appsec.securitychampionapp.app.membership

import java.time.Instant
import java.util.UUID

enum class ChannelAttentionCategory { NOT_IN_CHANNEL, DEACTIVATED_AFTER_LEAVING, IDENTITY_UNRESOLVED }

data class ChannelParticipantAttention(
    val participantId: UUID,
    val name: String,
    val email: String,
    val category: ChannelAttentionCategory,
    val absentSince: Instant?,
    val notificationStatus: ChannelNoticeStatus?,
)

data class ChannelCheckStatus(
    val lastAttemptAt: Instant,
    val lastSuccessAt: Instant?,
    val outcome: String,
    val failureSummary: String?,
)

data class SlackChannelParticipationOverview(
    val enabled: Boolean,
    val channelConfigured: Boolean,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?,
    val outcome: String?,
    val failureSummary: String?,
    val participants: List<ChannelParticipantAttention>,
)
