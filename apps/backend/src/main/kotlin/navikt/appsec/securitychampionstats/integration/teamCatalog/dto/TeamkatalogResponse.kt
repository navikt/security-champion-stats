package navikt.appsec.securitychampionstats.integration.teamCatalog.dto

import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor
import java.util.*

@Data
@NoArgsConstructor
@AllArgsConstructor
data class TeamResponse(
    val id: UUID?,
    val name: String?,
    val naisTeam: List<MemberResponse?>
)

@Data
@NoArgsConstructor
@AllArgsConstructor
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
data class ResourceResponse(
    val navIdent: String?,
    val fullName: String?,
    val email: String?
)
