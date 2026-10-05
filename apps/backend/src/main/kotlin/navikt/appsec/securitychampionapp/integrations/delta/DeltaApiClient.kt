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
) : DeltaRegistrationSource {
    override fun fetchEvent(eventUuid: UUID): DeltaEventRoster {
        if (tokenEndpoint.isBlank() || target.isBlank()) {
            throw DeltaIntegrationException(DeltaFailure.CONFIGURATION)
        }

        val token = acquireToken()

        val response = try {
            apiClient.get()
                .uri("/event/{eventUuid}", eventUuid)
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .onStatus(HttpStatusCode::isError) {
                    Mono.error(DeltaIntegrationException(DeltaFailure.API))
                }
                .bodyToMono<DeltaFullEventResponse>()
                .block()
                ?: throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        } catch (e: DeltaIntegrationException) {
            throw e
        } catch (_: WebClientRequestException) {
            throw DeltaIntegrationException(DeltaFailure.API)
        } catch (_: DecodingException) {
            throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        }

        if (response.event.id != eventUuid || response.participants.any { it.email.isBlank() }) {
            throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
        }

        return DeltaEventRoster(
            eventUuid = response.event.id,
            startTime = try {
                LocalDateTime.parse(response.event.startTime)
            } catch (_: RuntimeException) {
                throw DeltaIntegrationException(DeltaFailure.INVALID_RESPONSE)
            },
            participantEmails = response.participants.map { it.email.trim() }.distinctBy { it.lowercase() },
        )
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
}

data class DeltaEventRoster(
    val eventUuid: UUID,
    val startTime: LocalDateTime,
    val participantEmails: List<String>,
)

enum class DeltaFailure(val summary: String) {
    CONFIGURATION("Delta integration configuration is incomplete"),
    TOKEN("Delta service authentication failed"),
    API("Delta registration request failed"),
    INVALID_RESPONSE("Delta returned an invalid registration response"),
    PARTICIPANT_LOOKUP("Program participant data could not be loaded"),
}

class DeltaIntegrationException(
    val failure: DeltaFailure,
) : RuntimeException(failure.summary)

interface DeltaRegistrationSource {
    fun fetchEvent(eventUuid: UUID): DeltaEventRoster
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class NaisTokenResponse(
    @param:JsonProperty("access_token")
    val accessToken: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DeltaFullEventResponse(
    val event: DeltaEventResponse,
    val participants: List<DeltaParticipantResponse>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DeltaEventResponse(
    val id: UUID,
    val startTime: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class DeltaParticipantResponse(
    val email: String,
)
