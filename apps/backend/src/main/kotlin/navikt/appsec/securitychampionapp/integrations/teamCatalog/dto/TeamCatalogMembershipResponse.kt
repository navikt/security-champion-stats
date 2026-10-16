package navikt.appsec.securitychampionapp.integrations.teamCatalog.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamCatalogMembershipResponse(
    val teams: List<TeamCatalogDetailedTeam> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamCatalogDetailedTeam(
    val id: String,
    val name: String,
    val description: String? = null,
    val slackChannel: String? = null,
    val contactPersonIdent: String? = null,
    val contactAddresses: List<TeamCatalogContactAddress>? = null,
    val productAreaId: String? = null,
    val teamOwnerIdent: String? = null,
    val clusterIds: List<String>? = null,
    val teamType: String? = null,
    val qaTime: String? = null,
    val naisTeams: List<String>? = null,
    val members: List<TeamCatalogDetailedMember>? = null,
    val tags: List<String>? = null,
    val officeHours: TeamCatalogOfficeHoursResponse? = null,
    val status: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamCatalogDetailedMember(
    val navIdent: String? = null,
    val description: String? = null,
    val roles: List<String>? = null,
    val teamPercent: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val resource: TeamCatalogMemberResource? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamCatalogMemberResource(
    val navIdent: String? = null,
    val email: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamCatalogContactAddress(
    val address: String? = null,
    val type: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamCatalogOfficeHoursResponse(
    val location: TeamCatalogLocationResponse? = null,
    val days: List<String>? = null,
    val information: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamCatalogLocationResponse(
    val code: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TeamCatalogTeamUpdateRequest(
    val id: String,
    val name: String,
    val description: String?,
    val slackChannel: String?,
    val contactPersonIdent: String?,
    val contactAddresses: List<TeamCatalogContactAddress>?,
    val productAreaId: String?,
    val teamOwnerIdent: String?,
    val clusterIds: List<String>?,
    val teamType: String?,
    val qaTime: String?,
    val naisTeams: List<String>?,
    val members: List<TeamCatalogMemberUpdateRequest>,
    val tags: List<String>?,
    val officeHours: TeamCatalogOfficeHoursUpdateRequest?,
    val status: String?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TeamCatalogMemberUpdateRequest(
    val navIdent: String,
    val roles: List<String>,
    val description: String?,
    val teamPercent: Int?,
    val startDate: String?,
    val endDate: String?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TeamCatalogOfficeHoursUpdateRequest(
    val locationCode: String?,
    val days: List<String>?,
    val information: String?,
)
