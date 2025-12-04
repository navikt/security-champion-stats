package navikt.appsec.securitychampionstats.integration.teamCatalog.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor

@Data
@Serializable
@JsonIgnoreUnknownKeys
data class TeamResponse(
    val id: String?,
    val name: String?,
    val naisTeam: List<MemberResponse>?
)

@Data
@Serializable
@JsonIgnoreUnknownKeys
data class MemberResponse(
    val navIdent: String?,
    val roles: List<TeamRole?>,
    val resource: ResourceResponse?
)

@JsonIgnoreUnknownKeys
@Serializable
enum class TeamRole {
    SECURITY_CHAMPION,
    TEAM_MEMBER,
    OTHER
}

@Data
@Serializable
@JsonIgnoreUnknownKeys
data class ResourceResponse(
    val navIdent: String?,
    val fullName: String?,
    val email: String?
)
