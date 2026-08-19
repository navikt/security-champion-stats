package navikt.appsec.securitychampionapp.integrations.teamCatalog.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import lombok.Data

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TeamCatalogTeam(
    val members: List<MemberResponse>,
    val name: String,
    val id: String,
)

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberResponse(
    val roles: List<String>,
    val resource: ResourceResponse
)


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TeamResponse(
    val content: List<TeamCatalogTeam>
)

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceResponse(
    val navIdent: String,
    val fullName: String,
    val email: String?
)

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberWithTeamData(
    val navIdent: String,
    val fullName: String,
    val email: String,
    val teamName: MutableList<String>,
    val teamId: MutableList<String>
)