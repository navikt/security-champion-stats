package navikt.appsec.securitychampionstats.integration.zoom

import navikt.appsec.securitychampionstats.integration.zoom.dto.MeetingParticipantsResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class ZoomMeetingService(
    private val zoomAuthService: ZoomAuthService,
    private val zoomApiWebClient: WebClient
) {
    private val logger = LoggerFactory.getLogger(ZoomMeetingService::class.java)
    fun getLiveParticipants(meetingId: String): MeetingParticipantsResponse {
        val token = zoomAuthService.getAccessToken()
        if (token.isEmpty()) {
            logger.error("Failed to obtain access token for Zoom API")
            return MeetingParticipantsResponse(
                pageCount = 0,
                pageSize = 0,
                totalRecords = 0,
                nextPageToken = null,
                participants = emptyList()
            )
        }
        return try {
            zoomApiWebClient.get()
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
        } catch (e: Exception) {
            logger.error("Error fetching live participants from Zoom API: ${e.message}")
            MeetingParticipantsResponse(
                pageCount = 0,
                pageSize = 0,
                totalRecords = 0,
                nextPageToken = null,
                participants = emptyList()
            )
        }
    }
}