package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersListRequest
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersUpdateRequest
import com.slack.api.methods.request.users.UsersInfoRequest
import com.slack.api.methods.request.users.UsersLookupByEmailRequest
import com.slack.api.model.User
import navikt.appsec.securitychampionapp.app.membership.*
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.SlackIdentityMappingRepository
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.UUID

@Service
class SlackMembershipClient(
    private val client: MethodsClient,
    private val mappings: SlackIdentityMappingRepository,
    private val requests: SlackApiService,
) : SlackMembershipGateway, SlackParticipantDirectory {
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
        val users = mutableMapOf<UUID, String>()
        val unresolved = mutableSetOf<UUID>()
        participants.forEach { participant ->
            val accounts = approved[participant.id].orEmpty()
            val user = when {
                accounts.size > 1 -> null
                accounts.size == 1 -> userById(accounts.single().slackUserId)
                else -> userByEmail(participant.navNoEmail)
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

    private fun userById(userId: String): User? {
        val response = requests.call(SlackIntegrationException.Operation.USERS_INFO) {
            client.usersInfo(UsersInfoRequest.builder().user(userId).build())
        }
        if (response?.error == "user_not_found") return null
        if (response == null || !response.isOk) {
            throw SlackIntegrationException(SlackIntegrationException.Operation.USERS_INFO, response?.error)
        }
        return response.user
    }

    private fun userByEmail(email: String): User? {
        val response = requests.call(SlackIntegrationException.Operation.LOOKUP_BY_EMAIL) {
            client.usersLookupByEmail(UsersLookupByEmailRequest.builder().email(email).build())
        }
        if (response?.error == "users_not_found") return null
        if (response == null || !response.isOk) {
            throw SlackIntegrationException(SlackIntegrationException.Operation.LOOKUP_BY_EMAIL, response?.error)
        }
        val user = response.user ?: return null
        check(user.profile?.email?.equals(email, ignoreCase = true) == true) {
            "Slack email lookup returned an unverified identity"
        }
        return user
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
        val response = try {
            client.chatPostMessage(
                ChatPostMessageRequest.builder().channel(channelId).text(text)
                    .metadataAsString(
                        """{"event_type":"security_champion_membership","event_payload":{"delivery_id":"$deliveryId"}}""",
                    ).build(),
            )
        } catch (e: SlackApiException) {
            throw MembershipDeliveryException(
                e.response?.code.let { it == null || it >= 500 || it == 408 },
                "Slack membership announcement failed at the HTTP boundary",
            )
        } catch (_: IOException) {
            throw MembershipDeliveryException(true, "Slack membership announcement delivery is unknown after a network failure")
        }
        if (response == null || !response.isOk) {
            throw MembershipDeliveryException(
                response == null || response.error in setOf("internal_error", "fatal_error", "request_timeout"),
                SlackIntegrationException(SlackIntegrationException.Operation.POST_MESSAGE, response?.error).message.orEmpty(),
            )
        }
        return response.ts?.takeIf { it.isNotBlank() }
            ?: throw MembershipDeliveryException(true, "Slack membership announcement response has no message timestamp")
    }

}
