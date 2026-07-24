package navikt.appsec.securitychampionapp.integrations.teamCatalog

import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.ProductAreaResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamResponse
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class TeamCatalogMock(
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader,
) {
    private val logger = LoggerFactory.getLogger(TeamCatalogMock::class.java)

    fun loadMockProductAreas(): ProductAreaResponse {
        return try {
            resourceLoader.getResource("classpath:mock/teamCatalog/team_catalog_fetch_productArea.json").inputStream.use { input ->
                objectMapper.readValue(input, ProductAreaResponse::class.java)
            }
        } catch (e: Exception) {
            logger.error("Failed to load Teamkatalogen product area mock response", e)
            ProductAreaResponse(emptyList())
        }
    }

    fun loadMockMembersWithRole(productAreaResponse: ProductAreaResponse): List<TeamResponse> {
        return try {
            productAreaResponse.content.map { productArea ->
                resourceLoader.getResource("classpath:mock/teamCatalog/team_catalog_fetch_team_${productArea.id}.json")
                    .inputStream
                    .use { inputStream ->
                        objectMapper.readValue(inputStream, TeamResponse::class.java)
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to load Teamkatalogen members with role mock response", e)
            emptyList()
        }
    }
}