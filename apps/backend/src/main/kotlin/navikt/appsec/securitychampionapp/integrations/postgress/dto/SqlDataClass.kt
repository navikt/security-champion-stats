package navikt.appsec.securitychampionapp.integrations.postgress.dto

import navikt.appsec.securitychampionapp.app.api.dto.Event

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

data class ProgramParticipant(
    val id: String,
    val navNoEmail: String,
    val navIdent: String?,
    val email: String,
    val fullname: String,
    val teams: List<String>,
    val status: String,
    val createdAt: String,
)

data class ProgramParticipantQueryResponse(
    val isOk: Boolean,
    val queryResult: List<ProgramParticipant> = emptyList(),
    val error: String? = null,
)

data class ProgramParticipantUpdateResponse(
    val isOk: Boolean,
    val error: String? = null,
    val affectedRows: Int = 0,
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

data class EventQueryResponse(
    val isOk: Boolean,
    val queryResult: List<Event>? = null,
    val error: String? = null
)

data class EventUpdateResponse(
    val isOk: Boolean,
)

enum class EventType {
    WORKSHOP, MEETUP
}