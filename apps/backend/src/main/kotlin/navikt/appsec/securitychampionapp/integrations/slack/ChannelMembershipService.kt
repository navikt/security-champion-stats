package navikt.appsec.securitychampionapp.integrations.slack

import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.MarkdownTextObject
import com.slack.api.model.block.element.ImageElement
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresRepository
import navikt.appsec.securitychampionapp.integrations.slack.dto.SecurityChampion
import navikt.appsec.securitychampionapp.integrations.slack.dto.SecurityChampionMessage
import navikt.appsec.securitychampionapp.integrations.slack.dto.SlackResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ChannelMembershipService(
    private val slackApiService: SlackApiService,
    private val repo: PostgresRepository,
    @Value($$"${slack.appsec-activity-channel-id") private val scChannelId: String,
    @Value($$"${slack.userGroupId") private val userGrouping: String
) {

    private val logger = LoggerFactory.getLogger(ChannelMembershipService::class.java)



    fun updateUserGroupWithNewMembers(): SlackResponse {
        val userIds = mutableListOf<String>()
        val queryResponse = repo.getAllMembers()

        if (!queryResponse.isOk) {
            logger.warn("Failed to fetch all member from database due to error: ${queryResponse.error}")
            return SlackResponse(
                isOk = false,
                error = "Failed to fetch all member from database due to error: ${queryResponse.error}"
            )
        }

        val securityChampions = queryResponse.queryResult!!

        securityChampions.forEach { sc ->
            val response = slackApiService.fetchUserIdByEmail(sc.email)
            if (response != null) {
                userIds.add(response.user.id)
            }
        }

        if (userIds.isEmpty() || securityChampions.size != userIds.size) {
            logger.warn("Failed to fetch all users from slack, it could lead to potential fail update")
            return SlackResponse(
                isOk = false,
                error = "Failed to fetch all users from slack, it could lead to potential fail update"
            )
        }

        val response = slackApiService.updateUsersGroup(userIds, userGrouping)

        if (!response.isOk) {
            logger.error("failed to update user group due to some error: ${response.error}, check if user group is still correct")
            return SlackResponse(
                isOk = false,
                error= response.error
            )
        }

        return SlackResponse(
            isOk = true
        )
    }

    fun sendWelcomeMessage(secChampions: List<SecurityChampion>): SlackResponse {

        val simpleMessage = listOf(
            "Nye Security Champions:",
            *formatSimpleUserList(secChampions).toTypedArray()
        )

        val messageBlocks: List<LayoutBlock> = secChampions.map { sc ->
            userSlackBlock(
                sc,
                message = buildString {
                    append(
                        ":tada ${sc.teams} har fått seg en ny Security Champion!\n"
                    )
                    append(":security-champion: ${sc.fullname}")
                    append(" (<@${sc.email}")
                }
            )
        }

        val outroBlock = simpleBlock(
            """
                Velkommen!: :meow_wave: :security-pepperkake:
                Sjekk <<https://sikkerhet.nav.no/docs/ny-security-champion | «Ny Security Champion»> 
                for praktiske oppgaver å starte med :muscle:
            """.trimIndent()
        )

        val blocks: MutableList<LayoutBlock> = (messageBlocks + outroBlock).toMutableList()
        val response = slackApiService.postChatMessage(
            scChannelId,
            SecurityChampionMessage(
                fallBackBlock = simpleMessage.joinToString("\n"),
                messageBlock = blocks
            )
        )

        return response
    }


    private fun formatSimpleUserList(secChampions: List<SecurityChampion>): List<String> {
        return secChampions.map { sc ->
            "- <@${sc.email}> (<${sc.link} | ${sc.fullname}>)"
        }

    }

    private fun simpleBlock(text: String): LayoutBlock =
        SectionBlock.builder()
            .text(
                MarkdownTextObject.builder()
                    .text(text)
                    .build()
            )
            .build()

    fun userSlackBlock(
        secChampion: SecurityChampion?,
        message: String
    ): LayoutBlock {
        if (secChampion == null) {
            logger.warn("Missing security champion data, then creating message block")
            return  simpleBlock(message)
        }

        return SectionBlock.builder()
            .text(
                MarkdownTextObject.builder()
                    .text(message)
                    .build()
            )
            .accessory(
                ImageElement.builder()
                    .imageUrl(secChampion.imageUrl)
                    .altText(secChampion.fullname)
                    .build()
            ).build()
    }
}