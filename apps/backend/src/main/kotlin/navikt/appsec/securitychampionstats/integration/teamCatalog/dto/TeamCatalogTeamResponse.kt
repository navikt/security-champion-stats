package navikt.appsec.securitychampionstats.integration.teamCatalog.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import lombok.Data

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TeamResponse(
    val id: String?,
    val name: String?,
    val naisTeam: List<MemberResponse>?
)

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberResponse(
    val navIdent: String?,
    val roles: List<TeamRole?>,
    val resource: ResourceResponse?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class TeamRole {
    SECURITY_CHAMPION,
    TEAM_MEMBER,
    OTHER
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceResponse(
    val navIdent: String?,
    val fullName: String?,
    val email: String?
)
