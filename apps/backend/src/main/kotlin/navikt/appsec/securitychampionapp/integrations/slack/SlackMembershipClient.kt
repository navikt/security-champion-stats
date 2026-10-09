package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.request.conversations.ConversationsMembersRequest
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersListRequest
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersUpdateRequest
import com.slack.api.methods.request.users.UsersListRequest
import com.slack.api.model.User
import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.events.EventReminderGateway
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.SlackIdentityMappingRepository
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.UUID

@Service
class SlackMembershipClient(
    private val client: MethodsClient,
    private val mappings: SlackIdentityMappingRepository,
    private val requests: SlackApiService,
) : SlackMembershipGateway, SlackParticipantDirectory, EventReminderGateway, SlackChannelGateway {
    override fun members(usergroupId: String): Set<String> {
        val response = requests.call(SlackIntegrationException.Operation.GROUP_MEMBERS) {
            client.usergroupsUsersList(UsergroupsUsersListRequest.builder().usergroup(usergroupId).build())
        }
        if (response == null || !response.isOk) {
            throw SlackIntegrationException(SlackIntegrationException.Operation.GROUP_MEMBERS, response?.error)
        }
        return requireNotNull(response.users) { "Slack group membership response was incomplete" }.toSet()
    }

    override fun replaceMembers(usergroupId: String, users: Set<String>) {
        require(users.isNotEmpty()) { "Cannot replace Slack membership with an empty group" }
        val response = requests.call(SlackIntegrationException.Operation.GROUP_UPDATE) {
            client.usergroupsUsersUpdate(
                UsergroupsUsersUpdateRequest.builder().usergroup(usergroupId).users(users.sorted()).build(),
            )
        }
        if (response == null || !response.isOk) {
            throw SlackIntegrationException(SlackIntegrationException.Operation.GROUP_UPDATE, response?.error)
        }
    }

    override fun resolve(participants: List<ProgramParticipant>): SlackIdentityResolution {
        val approved = mappings.mappedParticipants().values.groupBy { it.participantId }
        val directory = readDirectory()
        val byId = directory.groupBy { it.id }
        val byEmail = directory.filter { !it.profile?.email.isNullOrBlank() }
            .groupBy { it.profile.email.lowercase() }
        val users = mutableMapOf<UUID, String>()
        val unresolved = mutableSetOf<UUID>()
        participants.forEach { participant ->
            val accounts = approved[participant.id].orEmpty()
            val user = when {
                accounts.size > 1 -> null
                accounts.size == 1 -> byId[accounts.single().slackUserId]?.singleOrNull()
                else -> byEmail[participant.navNoEmail.lowercase()]?.singleOrNull()
            }
            if (user == null || user.id.isNullOrBlank() || user.isDeleted || user.isBot ||
                user.isRestricted || user.isUltraRestricted
            ) {
                unresolved += participant.id
            } else {
                users[participant.id] = user.id
            }
        }
        val duplicateUserIds = users.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        users.filterValues { it in duplicateUserIds }.keys.forEach { unresolved += it }
        unresolved.forEach(users::remove)
        return SlackIdentityResolution(users, unresolved)
    }

    private fun readDirectory(): List<User> {
        val users = mutableListOf<User>()
        val cursors = mutableSetOf<String>()
        var cursor = ""
        do {
            val response = requests.call(SlackIntegrationException.Operation.USERS_LIST) {
                client.usersList(UsersListRequest.builder().limit(1000).cursor(cursor).build())
            }
            if (response?.isOk != true || response.members == null || response.responseMetadata?.nextCursor == null) {
                throw SlackIntegrationException(SlackIntegrationException.Operation.USERS_LIST, response?.error)
            }
            users.addAll(response.members)
            cursor = response.responseMetadata.nextCursor.trim()
            if (cursor.isNotEmpty() && !cursors.add(cursor)) {
                throw SlackIntegrationException(SlackIntegrationException.Operation.USERS_LIST, "repeated_cursor")
            }
        } while (cursor.isNotEmpty())
        return users
    }

    override fun announce(
        channelId: String,
        userId: String,
        kind: MembershipAnnouncementKind,
        deliveryId: UUID,
    ): String {
        val text = when (kind) {
            MembershipAnnouncementKind.WELCOME -> "Velkommen til Security Champion-programmet, <@$userId>!"
            MembershipAnnouncementKind.REMOVAL -> "<@$userId> er ikke lenger aktiv deltaker i Security Champion-programmet."
        }
        return postMessage(
            ChatPostMessageRequest.builder().channel(channelId).text(text)
                .metadataAsString(
                    """{"event_type":"security_champion_membership","event_payload":{"delivery_id":"$deliveryId"}}""",
                ).build(),
        )
    }

    override fun channelMembers(channelId: String): Set<String> {
        val members = mutableSetOf<String>()
        val cursors = mutableSetOf<String>()
        var cursor = ""
        do {
            val response = requests.call(SlackIntegrationException.Operation.CHANNEL_MEMBERS) {
                client.conversationsMembers(
                    ConversationsMembersRequest.builder().channel(channelId).limit(200).cursor(cursor).build(),
                )
            }
            if (response?.isOk != true || response.members == null) {
                throw SlackIntegrationException(SlackIntegrationException.Operation.CHANNEL_MEMBERS, response?.error)
            }
            members.addAll(response.members)
            cursor = response.responseMetadata?.nextCursor?.trim().orEmpty()
            if (cursor.isNotEmpty() && !cursors.add(cursor)) {
                throw SlackIntegrationException(SlackIntegrationException.Operation.CHANNEL_MEMBERS, "repeated_cursor")
            }
        } while (cursor.isNotEmpty())
        return members
    }

    override fun notifyChannelDeparture(slackUserId: String, channelId: String, deliveryId: UUID): String =
        postMessage(
            ChatPostMessageRequest.builder().channel(slackUserId)
                .text(
                    "Hei! Vi forventer at alle Security Champions er med i <#$channelId> og følger med på det som " +
                        "deles der. Du er ikke lenger medlem av kanalen, så deltakelsen din i Security Champion-" +
                        "programmet er deaktivert. Historikken og poengene dine er tatt vare på. Blir du med i " +
                        "kanalen igjen, aktiveres deltakelsen automatisk ved neste kontroll.",
                )
                .unfurlLinks(false).unfurlMedia(false)
                .metadataAsString(
                    """{"event_type":"security_champion_channel_departure","event_payload":{"delivery_id":"$deliveryId"}}""",
                ).build(),
        )

    override fun remind(slackUserId: String, text: String, deliveryId: UUID): String =
        postMessage(
            ChatPostMessageRequest.builder().channel(slackUserId).text(text)
                .unfurlLinks(false).unfurlMedia(false)
                .metadataAsString(
                    """{"event_type":"security_champion_event_reminder","event_payload":{"delivery_id":"$deliveryId"}}""",
                ).build(),
        )

    private fun postMessage(request: ChatPostMessageRequest): String {
        val response = try {
            client.chatPostMessage(request)
        } catch (e: SlackApiException) {
            throw MembershipDeliveryException(
                e.response?.code.let { it == null || it >= 500 || it == 408 },
                "Slack message failed at the HTTP boundary",
                stopBatch = true,
                retryAfter = Duration.ofSeconds(
                    e.response?.header("Retry-After")?.toLongOrNull()?.coerceAtLeast(1) ?: 900,
                ),
            )
        } catch (_: IOException) {
            throw MembershipDeliveryException(true, "Slack message delivery is unknown after a network failure")
        }
        if (response == null || !response.isOk) {
            throw MembershipDeliveryException(
                response == null || response.error in setOf("internal_error", "fatal_error", "request_timeout"),
                SlackIntegrationException(SlackIntegrationException.Operation.POST_MESSAGE, response?.error).message.orEmpty(),
                stopBatch = response?.error !in setOf("invalid_user", "user_not_found", "user_disabled", "cannot_dm_bot"),
            )
        }
        return response.ts?.takeIf { it.isNotBlank() }
            ?: throw MembershipDeliveryException(true, "Slack message response has no message timestamp")
    }

}
