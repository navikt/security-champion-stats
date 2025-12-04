package navikt.appsec.securitychampionstats.integration.teams.dto

import java.time.OffsetDateTime

data class MeetingAttendanceReport(
    val id: String?,
    val meetingStartDateTime: OffsetDateTime?,
    val meetingEndDateTime: OffsetDateTime?,
    val totalParticipantCount: Int?,
    val attendanceRecords: List<AttendanceRecord>?
)

data class TokenResponse(
    val tokenType: String?,
    val expiresIn: Long?,
    val extExpiresIn: Long?,
    val accessToken: String?
)

data class AttendanceRecord(
    val id: String?,
    val emailAddress: String?,
    val role: String?,
    val totalAttendanceInSeconds: Int?,
    val attendanceIntervals: List<AttendanceInterval>?
)

data class AttendanceInterval(
    val joinDateTime: OffsetDateTime?,
    val leaveDateTime: OffsetDateTime?,
    val durationInSeconds: Int?
)

data class MeetingAttendanceReportListResponse(
    val value: List<MeetingAttendanceReport>
)

data class AttendanceRecordListResponse(
    val value: List<AttendanceRecord>
)