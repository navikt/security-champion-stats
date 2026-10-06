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

    override fun pastEventsInCategory(categoryId: Int): List<DeltaEventRegistrations> {
        if (categoryId <= 0) throw DeltaIntegrationException(DeltaFailure.CONFIGURATION)

        val response = apiRequest { token ->
            apiClient.get()
                .uri { uri ->
                    uri.path("/event")
                        .queryParam("categories", categoryId)
                        .queryParam("onlyPast", true)
                        .build()
                }
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .onStatus(HttpStatusCode::isError) {
                    Mono.error(DeltaIntegrationException(DeltaFailure.API))
                }
                .bodyToMono<List<DeltaFullEventResponse>>()
                .block()
                ?: throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        }
        return response.map { it.toRegistrations() }
    }

    override fun event(eventId: UUID): DeltaEventRegistrations? {
        val response = apiRequest { token ->
            apiClient.get()
                .uri("/event/{id}", eventId)
                .headers { it.setBearerAuth(token) }
                .exchangeToMono { response ->
                    when {
                        response.statusCode().value() == 404 -> response.releaseBody().then(Mono.empty())
                        response.statusCode().isError ->
                            response.releaseBody().then(Mono.error(DeltaIntegrationException(DeltaFailure.API)))
                        else -> response.bodyToMono<DeltaFullEventResponse>()
                    }
                }
                .block()
        }
        return response?.toRegistrations()
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

data class DeltaEventRegistrations(
    val eventUuid: UUID,
    val startTime: LocalDateTime,
    val participantEmails: Set<String>,
)

enum class DeltaFailure(val summary: String) {
    CONFIGURATION("Delta integration configuration is incomplete"),
    TOKEN("Delta service authentication failed"),
    API("Delta registration request failed"),
    INVALID_RESPONSE("Delta returned an invalid registration response"),
    PARTICIPANT_LOOKUP("Program participant data could not be loaded"),
    EVENT_NOT_FOUND("A mapped Delta event was not found or is not public"),
}

class DeltaIntegrationException(
    val failure: DeltaFailure,
) : RuntimeException(failure.summary)

interface DeltaRegistrationSource {
    fun pastEventsInCategory(categoryId: Int): List<DeltaEventRegistrations>

    fun event(eventId: UUID): DeltaEventRegistrations?
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
private data class DeltaFullEventResponse(
    val event: DeltaEventDetailsResponse,
    val participants: List<DeltaParticipantResponse> = emptyList(),
) {
    fun toRegistrations() = DeltaEventRegistrations(
        eventUuid = event.id,
        startTime = try {
            LocalDateTime.parse(event.startTime)
        } catch (_: RuntimeException) {
            throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        },
        participantEmails = participants.mapNotNull { it.email?.trim()?.takeIf(String::isNotEmpty) }.toSet(),
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DeltaEventDetailsResponse(
    val id: UUID,
    val startTime: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DeltaParticipantResponse(
    val email: String?,
)
