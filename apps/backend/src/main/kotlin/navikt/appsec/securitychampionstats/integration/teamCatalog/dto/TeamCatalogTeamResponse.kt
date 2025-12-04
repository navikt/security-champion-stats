package navikt.appsec.securitychampionstats.integration.teamCatalog.dto

import kotlinx.serialization.Serializable
import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor

@Data
@NoArgsConstructor
@AllArgsConstructor
@Serializable
data class TeamResponse(
    val id: String?,
    val name: String?,
    val naisTeam: List<MemberResponse>?
)

@Data
@NoArgsConstructor
@AllArgsConstructor
@Serializable
data class MemberResponse(
    val navIdent: String?,
    val roles: List<TeamRole?>,
    val resource: ResourceResponse?
)

enum class TeamRole {
    SECURITY_CHAMPION,
    TEAM_MEMBER,
    OTHER
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Serializable
data class ResourceResponse(
    val navIdent: String?,
    val fullName: String?,
    val email: String?
)
