package navikt.appsec.securitychampionstats.models

import kotlinx.serialization.Serializable

@Serializable
data class ResourceMember(
    val id: String,
    val roles: List<String>,
    val members: List<Resource>,
)

@Serializable
data class Resource(
    val navIdent: String,
    val email: String,
    val fullname: String,
    val resourceType: ResourceType,
    val endDate: String?,
)
@Serializable
data class ResourceGroup(
    val id: String,
    val name: String,
    val members: List<ResourceMember>,
    val links: Link
)

@Serializable
data class Link(
    val ui: String
)

@Serializable
enum class ResourceType {
    INTERNAL,
    EXTERNAL,
}

@Serializable
enum class TeamkatalogResourceType {
    TEAM,
    PRODUCTAREA,
    CLUSTER,
}

@Serializable
data class ResourceMemberWithGroup(
    val group: ResourceGroup,
    val members: ResourceMember
)