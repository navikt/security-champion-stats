package navikt.appsec.securitychampionstats.integration.teams

import navikt.appsec.securitychampionstats.integration.teams.dto.AttendanceRecord
import navikt.appsec.securitychampionstats.integration.teams.dto.AttendanceRecordListResponse
import navikt.appsec.securitychampionstats.integration.teams.dto.MeetingAttendanceReport
import navikt.appsec.securitychampionstats.integration.teams.dto.MeetingAttendanceReportListResponse
import navikt.appsec.securitychampionstats.integration.teams.dto.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.OffsetDateTime

@Component
class GraphClient(
    @Value("\${azure.tenantId}") private val tenantId: String,
    @Value("\${azure.clientId}") private val clientId: String,
    @Value("\${azure.clientSecret}") private val clientSecret: String,
    @Value("\${graph.apiUrl}") private val apiUrl: String
) {
    private val logger = LoggerFactory.getLogger("GraphClient")
    private val webClient: WebClient = WebClient.builder().build()

    private fun getAccessToken(): String {
        return try {
            val response = webClient.post()
                .uri("https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("grant_type", "client_credentials")
                        .with("scope", "https://graph.microsoft.com/.default")
                )
                .retrieve()
                .onStatus({ status -> status.isError }) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { body ->
                        RuntimeException("Error from Teams token endpoint: ${clientResponse.statusCode()} body=$body")
                    }
                }
                .bodyToMono<TokenResponse>()
                .block()
            response?.accessToken.orEmpty()
        } catch(e: Exception) {
            logger.error(e.message)
            return ""
        }
    }

    private fun getListMeetingAttendanceReports(
        meetingId: String,
        token: String
    ): List<MeetingAttendanceReport> {
        return try {
            val response = webClient.get()
                .uri("$apiUrl/me/onlineMeetings/$meetingId/attendanceReports")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .onStatus({ status -> status.isError }) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { body ->
                        RuntimeException("Error from Graph API attendance reports endpoint: ${clientResponse.statusCode()} body=$body")
                    }
                }
                .bodyToMono<MeetingAttendanceReportListResponse>()
                .block()
            response?.value ?: emptyList()
        } catch (e: Exception) {
            logger.error(e.message)
            emptyList()
        }
    }

    fun getListAttendanceReport(
        meetingId: String,
        meetingDate: OffsetDateTime
    ): List<AttendanceRecord> {
        val token = getAccessToken()
        if (token.isEmpty()) {
            logger.error("Failed to obtain access token for Graph API")
            return emptyList()
        }

        val meetingReportList = getListMeetingAttendanceReports(meetingId, token)

        if (meetingReportList.isEmpty()) {
            logger.info("No meeting reports found for meetingId: $meetingId")
            return emptyList()
        }

        val report = meetingReportList.find { it.meetingStartDateTime?.isEqual(meetingDate) ?: false }

        if (report == null) {
            logger.info("No matching meeting report found for meetingId: $meetingId at date: $meetingDate")
            return emptyList()
        }

        return try {
            val response = webClient.get()
                .uri("/onlineMeetings/${meetingId}/attendanceReports/${report.id!!}?\$expand=attendanceRecords")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .onStatus({ status -> status.isError }) { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { body ->
                        RuntimeException("Error from Graph API attendance reports endpoint: ${clientResponse.statusCode()} body=$body")
                    }
                }
                .bodyToMono<AttendanceRecordListResponse>()
                .block()

            response?.value ?: emptyList()
        } catch (e: Exception) {
            logger.error(e.message)
            emptyList()
        }

    }

}