package navikt.appsec.securitychampionapp.app.membership

data class TeamCatalogTeamMembership(
    val id: String,
    val name: String,
    val isSecurityChampion: Boolean,
)

data class TeamCatalogRolePayloadPreview(
    val id: String,
    val name: String,
    val teamMemberCount: Int,
    val currentMemberRoles: List<String>,
)

enum class TeamCatalogRoleOperation {
    ADD,
    REMOVE,
}

enum class TeamCatalogRoleUpdateStatus {
    PREVIEW,
    UPDATED,
    FAILED,
}

data class TeamCatalogRoleUpdate(
    val teamId: String,
    val teamName: String,
    val operation: TeamCatalogRoleOperation,
    val status: TeamCatalogRoleUpdateStatus,
    val payloadPreview: TeamCatalogRolePayloadPreview,
)

data class TeamCatalogRoleState(
    val teams: List<TeamCatalogTeamMembership>,
    val writeEnabled: Boolean,
    val dryRun: Boolean,
)

data class TeamCatalogRoleSaveResult(
    val teams: List<TeamCatalogTeamMembership>,
    val updates: List<TeamCatalogRoleUpdate>,
    val writeEnabled: Boolean,
    val dryRun: Boolean,
)

class InvalidTeamCatalogSelectionException : RuntimeException()

data class TeamCatalogChampionStatus(
    val participantId: String,
    val status: ChampionRole,
)

data class TeamCatalogChampionStatusOverview(
    val participants: List<TeamCatalogChampionStatus>,
)

interface TeamCatalogRoleManagement {
    fun memberships(email: String, navIdent: String): List<TeamCatalogTeamMembership>

    fun saveSelection(
        email: String,
        navIdent: String,
        selectedTeamIds: Set<String>,
        writeEnabled: Boolean,
    ): TeamCatalogRoleSaveResult
}
