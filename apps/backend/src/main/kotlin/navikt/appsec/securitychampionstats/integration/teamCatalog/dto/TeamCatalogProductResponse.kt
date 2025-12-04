package navikt.appsec.securitychampionstats.integration.teamCatalog.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductAreaResponse(val content: List<ProductArea>)

@Serializable
data class ProductArea(val id: String, val name: String)
