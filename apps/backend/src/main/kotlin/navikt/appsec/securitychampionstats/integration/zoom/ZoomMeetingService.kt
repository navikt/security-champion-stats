package navikt.appsec.securitychampionstats.integration.zoom

import navikt.appsec.securitychampionstats.integration.zoom.dto.MeetingParticipantsResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class ZoomMeetingService(
    private val zoomAuthService: ZoomAuthService,
    private val zoomApiWebClient: WebClient
) {
    fun getLiveParticipants(meetingId: String): MeetingParticipantsResponse {
        val token = zoomAuthService.getAccessToken()

        return zoomApiWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/metrics/meetings/{meetingId}/participants")
                    .queryParam("type", "liive")
                    .build(meetingId)
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .bodyToMono<MeetingParticipantsResponse>()
            .block() ?: MeetingParticipantsResponse(
                pageCount = 0,
                pageSize = 0,
                totalRecords = 0,
                nextPageToken = null,
                participants = emptyList()
            )
    }
}