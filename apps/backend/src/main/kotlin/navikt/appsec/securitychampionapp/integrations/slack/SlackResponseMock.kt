package navikt.appsec.securitychampionapp.integrations.slack

import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class SlackResponseMock(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val environment: Environment,
) {

    private val logger = LoggerFactory.getLogger(SlackResponseMock::class.java)

    fun useMockResponses(): Boolean = environment.acceptsProfiles(Profiles.of("local", "test"))

    fun fetchMockData(path: String, valueType: Any): Any? {
        val response = try {
            resourceLoader.getResource(path)
                .inputStream
                .use { inputStream ->
                    objectMapper.readValue(inputStream, valueType::class.java)
                }
        } catch (e: Exception) {
            logger.error("Failed to load Slack mock response", e)
            null
        }
        return response
    }
}