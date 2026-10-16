package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.config.TeamCatalogRoleProperties
import org.springframework.stereotype.Service

class TeamCatalogRoleAccessException : RuntimeException()

class TeamCatalogRolesDisabledException : RuntimeException()

@Service
class TeamCatalogRoleService(
    private val participants: ParticipantStore,
    private val roles: TeamCatalogRoleManagement,
    private val roleSource: ChampionRoleSource,
    private val properties: TeamCatalogRoleProperties,
) {
    fun currentTeams(email: String, navIdent: String): TeamCatalogRoleState {
        requireEnabled()
        requireActiveParticipant(email)
        return TeamCatalogRoleState(
            teams = roles.memberships(email, navIdent),
            writeEnabled = !properties.dryRun,
            dryRun = properties.dryRun,
        )
    }

    fun saveSelection(
        email: String,
        navIdent: String,
        selectedTeamIds: Set<String>,
    ): TeamCatalogRoleSaveResult {
        requireEnabled()
        requireActiveParticipant(email)
        val writeEnabled = !properties.dryRun
        return roles.saveSelection(email, navIdent, selectedTeamIds, writeEnabled).copy(
            writeEnabled = writeEnabled,
            dryRun = !writeEnabled,
        )
    }

    fun championStatus(): TeamCatalogChampionStatusOverview {
        requireEnabled()
        val roleSnapshot = roleSource.fetchRoles()
        val activeParticipants = participants.findActiveParticipants()
        return TeamCatalogChampionStatusOverview(
            participants = activeParticipants.map { participant ->
                TeamCatalogChampionStatus(
                    participantId = participant.id.toString(),
                    status = roleSnapshot[participant.navNoEmail.lowercase()] ?: ChampionRole.UNKNOWN,
                )
            },
        )
    }

    private fun requireEnabled() {
        if (!properties.enabled) throw TeamCatalogRolesDisabledException()
    }

    private fun requireActiveParticipant(email: String) {
        if (participants.findByNavNoEmail(email)?.status != ParticipationStatus.ACTIVE) {
            throw TeamCatalogRoleAccessException()
        }
    }
}
