package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import org.springframework.stereotype.Service
import java.util.UUID

fun interface SlackParticipantLookup {
    fun findEligibleUser(email: String): String?
}

interface SlackIdentityStore {
    fun mappedParticipantIds(): Set<UUID>
    fun saveVerifiedMapping(slackUserId: String, participant: ProgramParticipant): Boolean
}

data class SlackIdentityProvisioningSummary(val mapped: Int, val unresolved: Int, val skipped: Int)

@Service
class SlackIdentityProvisioningService(
    private val participants: ParticipantStore,
    private val identities: SlackIdentityStore,
    private val lookup: SlackParticipantLookup,
) {
    fun provision(participantId: UUID? = null): SlackIdentityProvisioningSummary {
        val mappedIds = identities.mappedParticipantIds()
        val candidates = if (participantId == null) participants.findActiveParticipants()
            else listOfNotNull(participants.findById(participantId))
        var mapped = 0
        var unresolved = 0
        var skipped = 0
        candidates.forEach { participant ->
            if (participant.status != ParticipationStatus.ACTIVE || participant.id in mappedIds) {
                skipped++
                return@forEach
            }
            val userId = lookup.findEligibleUser(participant.navNoEmail)
            if (userId == null) {
                unresolved++
            } else if (identities.saveVerifiedMapping(userId, participant)) {
                mapped++
            } else {
                val current = participants.findById(participant.id)
                if (current?.status != ParticipationStatus.ACTIVE || participant.id in identities.mappedParticipantIds()) skipped++
                else unresolved++
            }
        }
        return SlackIdentityProvisioningSummary(mapped, unresolved, skipped)
    }
}
