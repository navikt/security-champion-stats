package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import org.slf4j.LoggerFactory
import java.util.UUID

data class SlackIdentityResolution(
    val users: Map<UUID, String>,
    val unresolvedParticipantIds: Set<UUID>,
)

fun interface SlackParticipantDirectory {
    fun resolve(participants: List<ProgramParticipant>): SlackIdentityResolution
}

interface SlackMembershipGateway {
    fun members(usergroupId: String): Set<String>
    fun replaceMembers(usergroupId: String, users: Set<String>)
    fun announce(channelId: String, userId: String, kind: MembershipAnnouncementKind, deliveryId: UUID): String
}

class MembershipDeliveryException(val uncertain: Boolean, message: String) : RuntimeException(message)
class MembershipSyncBusyException : RuntimeException("Slack membership sync is running")

enum class ChampionRole { PRESENT, ABSENT, UNKNOWN }

fun interface ChampionRoleSource {
    fun fetchRoles(): Map<String, ChampionRole>
}

enum class MembershipAnnouncementKind { WELCOME, REMOVAL }
enum class MembershipDeliveryStatus { PENDING, SENDING, SENT, SUPPRESSED, CANCELLED, UNCERTAIN }

data class MembershipAnnouncement(
    val id: UUID,
    val participantId: UUID,
    val slackUserId: String,
    val kind: MembershipAnnouncementKind,
    val status: MembershipDeliveryStatus,
)

interface SlackMembershipStore {
    fun observe(usergroupId: String, activeUsers: Map<UUID, String>)
    fun announcements(usergroupId: String): List<MembershipAnnouncement>
    fun updateDelivery(id: UUID, status: MembershipDeliveryStatus, messageTs: String? = null)
    fun recoverInterruptedDeliveries(usergroupId: String)
}

data class SlackMembershipPreview(
    val addedUserIds: Set<String>,
    val removedUserIds: Set<String>,
    val unresolvedParticipantIds: Set<UUID>,
    val activeParticipants: Int,
)

data class SlackMembershipSettings(
    val usergroupId: String,
    val welcomeChannelId: String,
    val adminChannelId: String,
) {
    fun validate() {
        require(usergroupId.isNotBlank() && welcomeChannelId.isNotBlank() && adminChannelId.isNotBlank()) {
            "Slack membership group and announcement channel IDs must be configured"
        }
    }
}

class SlackMembershipService(
    private val participants: ParticipantStore,
    private val directory: SlackParticipantDirectory,
    private val slack: SlackMembershipGateway,
    private val roles: ChampionRoleSource,
    private val state: SlackMembershipStore,
    private val settings: SlackMembershipSettings,
) {
    private val logger = LoggerFactory.getLogger(SlackMembershipService::class.java)

    fun sync(dryRun: Boolean): SlackMembershipPreview {
        val active = participants.findActiveParticipants()
        check(active.isNotEmpty()) {
            "Slack membership sync refuses zero active participants"
        }
        settings.validate()
        val resolution = directory.resolve(active)
        val desired = resolution.users.values.toSet()
        val existing = slack.members(settings.usergroupId)
        val preview = SlackMembershipPreview(
            desired - existing,
            existing - desired,
            resolution.unresolvedParticipantIds,
            active.size,
        )
        if (dryRun) return preview
        check(resolution.unresolvedParticipantIds.isEmpty() && resolution.users.keys == active.map { it.id }.toSet()) {
            "Slack membership sync blocked by unresolved participant identities"
        }
        check(desired.size == active.size) { "Slack membership sync found conflicting Slack identities" }
        check(participants.findActiveParticipants().associate { it.id to it.navNoEmail } ==
            active.associate { it.id to it.navNoEmail }) {
            "Participation changed during Slack membership sync; retry with a fresh snapshot"
        }
        state.recoverInterruptedDeliveries(settings.usergroupId)
        state.observe(settings.usergroupId, resolution.users)
        if (desired != existing) slack.replaceMembers(settings.usergroupId, desired)
        deliverAnnouncements(resolution.users)
        return preview
    }

    private fun deliverAnnouncements(activeUsers: Map<UUID, String>) {
        val pending = state.announcements(settings.usergroupId)
            .filter { it.status == MembershipDeliveryStatus.PENDING }
        if (pending.isEmpty()) return
        val roleSnapshot = roles.fetchRoles()
        pending.forEach { announcement ->
            val participant = participants.findAllParticipants().firstOrNull { it.id == announcement.participantId }
            val active = participant?.status == ParticipationStatus.ACTIVE
            if (participant == null || active != (announcement.kind == MembershipAnnouncementKind.WELCOME)) {
                state.updateDelivery(announcement.id, MembershipDeliveryStatus.CANCELLED)
                return@forEach
            }
            when (roleSnapshot[participant.navNoEmail.lowercase()] ?: ChampionRole.UNKNOWN) {
                ChampionRole.PRESENT -> state.updateDelivery(announcement.id, MembershipDeliveryStatus.SUPPRESSED)
                ChampionRole.UNKNOWN -> logger.warn(
                    "Deferring Slack membership announcement because Teamkatalogen role is unknown (delivery={})",
                    announcement.id,
                )
                ChampionRole.ABSENT -> {
                    val userId = if (announcement.kind == MembershipAnnouncementKind.WELCOME) {
                        activeUsers.getValue(announcement.participantId)
                    } else {
                        announcement.slackUserId
                    }
                    val channel = if (announcement.kind == MembershipAnnouncementKind.WELCOME) {
                        settings.welcomeChannelId
                    } else {
                        settings.adminChannelId
                    }
                    state.updateDelivery(announcement.id, MembershipDeliveryStatus.SENDING)
                    val messageTs = try {
                        slack.announce(channel, userId, announcement.kind, announcement.id)
                    } catch (e: MembershipDeliveryException) {
                        state.updateDelivery(
                            announcement.id,
                            if (e.uncertain) MembershipDeliveryStatus.UNCERTAIN else MembershipDeliveryStatus.PENDING,
                        )
                        throw e
                    }
                    state.updateDelivery(announcement.id, MembershipDeliveryStatus.SENT, messageTs)
                }
            }
        }
    }
}
