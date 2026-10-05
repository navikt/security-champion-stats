package navikt.appsec.securitychampionapp.integrations.delta

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class DeltaApiClient(
    private val apiClient: WebClient,
    private val tokenClient: WebClient,
    private val tokenEndpoint: String,
    private val target: String,
) : DeltaRegistrationSource, DeltaCategorySource {
    override fun categories(): List<DeltaCategory> {
        val response = apiRequest { token ->
            apiClient.get()
                .uri("/category")
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .onStatus(HttpStatusCode::isError) {
                    Mono.error(DeltaIntegrationException(DeltaFailure.API))
                }
                .bodyToMono<List<DeltaCategory>>()
                .block()
                ?: throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        }
        if (response.any { it.id <= 0 || it.name.isBlank() }) {
            throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        }
        return response
    }

    override fun findRegisteredEvents(
        categoryId: Int,
        participantEmail: String,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<DeltaEventMatch> {
        if (categoryId <= 0 || participantEmail.isBlank() || !from.isBefore(to)) {
            throw DeltaIntegrationException(DeltaFailure.CONFIGURATION)
        }

        val response = apiRequest { token ->
            apiClient.get()
                .uri { uri ->
                    uri.path("/event")
                        .queryParam("categories", categoryId)
                        .queryParam("participantEmail", participantEmail)
                        .queryParam("from", from.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .queryParam("to", to.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .build()
                }
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .onStatus(HttpStatusCode::isError) {
                    Mono.error(DeltaIntegrationException(DeltaFailure.API))
                }
                .bodyToMono<List<DeltaEventResponse>>()
                .block()
                ?: throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        }

        return response.map { event ->
            DeltaEventMatch(
                eventUuid = event.event.id,
                startTime = try {
                    LocalDateTime.parse(event.event.startTime)
                } catch (_: RuntimeException) {
                    throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
                },
            )
        }
    }

    private fun acquireToken(): String =
        try {
            tokenClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    BodyInserters.fromFormData("identity_provider", "entra_id")
                        .with("target", target)
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) {
                    Mono.error(DeltaIntegrationException(DeltaFailure.TOKEN))
                }
                .bodyToMono<NaisTokenResponse>()
                .block()
                ?.accessToken
                ?.takeIf(String::isNotBlank)
                ?: throw DeltaIntegrationException(DeltaFailure.TOKEN)
        } catch (e: DeltaIntegrationException) {
            throw e
        } catch (_: WebClientRequestException) {
            throw DeltaIntegrationException(DeltaFailure.TOKEN)
        } catch (_: DecodingException) {
            throw DeltaIntegrationException(DeltaFailure.TOKEN)
        }

    private fun <T> apiRequest(request: (String) -> T): T =
        try {
            if (tokenEndpoint.isBlank() || target.isBlank()) {
                throw DeltaIntegrationException(DeltaFailure.CONFIGURATION)
            }
            request(acquireToken())
        } catch (e: DeltaIntegrationException) {
            throw e
        } catch (_: WebClientRequestException) {
            throw DeltaIntegrationException(DeltaFailure.API)
        } catch (_: DecodingException) {
            throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        }
}

data class DeltaCategory(
    val id: Int,
    val name: String,
)

data class DeltaEventMatch(
    val eventUuid: UUID,
    val startTime: LocalDateTime,
)

enum class DeltaFailure(val summary: String) {
    CONFIGURATION("Delta integration configuration is incomplete"),
    TOKEN("Delta service authentication failed"),
    API("Delta registration request failed"),
    INVALID_RESPONSE("Delta returned an invalid registration response"),
    PARTICIPANT_LOOKUP("Program participant data could not be loaded"),
    MAPPING_CATEGORY("A Delta event mapping has no category selected"),
}

class DeltaIntegrationException(
    val failure: DeltaFailure,
) : RuntimeException(failure.summary)

interface DeltaRegistrationSource {
    fun findRegisteredEvents(
        categoryId: Int,
        participantEmail: String,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<DeltaEventMatch>
}

interface DeltaCategorySource {
    fun categories(): List<DeltaCategory>
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class NaisTokenResponse(
    @param:JsonProperty("access_token")
    val accessToken: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DeltaEventResponse(
    val event: DeltaEventDetailsResponse,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DeltaEventDetailsResponse(
    val id: UUID,
    val startTime: String,
)
