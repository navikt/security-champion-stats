package navikt.appsec.securitychampionapp.integrations.postgress.dto

import java.time.Instant

data class SqlTextArray(
    val value: Collection<String>
)

data class SqlMember(
    val id: String,
    val fullname: String,
    val points: Int,
    val lastUpdated: String,
    val email: String,
    val inProgram: Boolean,
    val level: String,
    val teams: List<String>,
    val createdAt: String,
)

data class MemberQueryResponse(
    val isOk: Boolean,
    val queryResult: List<SqlMember>? = null,
    val error: String? = null
)

data class MemberUpdateResponse(
    val isOk: Boolean,
    val error: String? = null
)

data class SqlEvent(
    val id: String,
    val name: String,
    val description: String,
    val startDateTime: Instant,
    val endDateTime: Instant,
    val location: String,
    val externalEvent: Boolean,
    val deltaEvent: Boolean,
    val type: EventType
)

data class EventQueryResponse(
    val isOk: Boolean,
    val queryResult: List<SqlEvent>? = null,
    val error: String? = null
)

data class EventUpdateResponse(
    val isOk: Boolean,
)

enum class EventType {
    WORKSHOP, MEETING
}