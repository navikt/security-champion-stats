package navikt.appsec.securitychampionstats.integration.zoom.dto

data class MeetingParticipantsResponse(
    val pageCount: Int?,
    val pageSize: Int?,
    val totalRecords: Int?,
    val nextPageToken: String?,
    val participants: List<Participant>?
)

data class Participant(
    val id: String?,
    val userId: String?,
    val userName: String?,
    val email: String?,
    val joinTime: String?,
    val leaveTime: String?,
    val device: String?,
    val ipAddress: String?,
    val location: String?
)