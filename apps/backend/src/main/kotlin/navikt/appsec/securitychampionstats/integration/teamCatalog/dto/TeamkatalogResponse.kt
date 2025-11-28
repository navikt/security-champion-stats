package navikt.appsec.securitychampionstats.integration.teamCatalog.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Team(
    val id: String?,
    val name: String?,
    val description: String?,
    val slackChannel: String?,
    val contactPersonIdent: String?,
    val productAreaId: String?,
    val avdelingNomId: String?,
    val avdelingNavn: String?,
    val teamOwnerIdent: String?,
    val clusterIds: List<String?>,
    val teamType: String?,
    val qaTime: String?,
    val naisTeams: List<String?>,
    val members: List<Member?>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Member(
    val navIdent: String,
    val description: String?,
    val roles: List<String>,
    val teamPercent: Int?,
    val startDate: String?,
    val endDate: String?,
    val resource: Resource
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Resource(
    val navIdent: String,
    val givenName: String?,
    val familyName: String?,
    val fullName: String,
    val email: String?,
    val onLeave: Boolean?,
    val resourceType: String?,
    val startDate: String?,
    val endDate: String?,
    val stale: Boolean?
)