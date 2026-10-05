package navikt.appsec.securitychampionapp.app.api.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Member(
    val id: String,
    val email: String,
    val points: Int,
    val fullname: String,
    val level: String = "1",
    val inGame: Boolean = false,
    val joinedAt: String,
    val teams: List<String> = emptyList()
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AddMember(val fullName: String, val email: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SCdata(val timestamp: String, val amount: Int)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Me(val username: String, val isAdmin: Boolean, val isParticipant: Boolean, val isActive: Boolean)

data class ProgramParticipantSummary(
    val id: String,
    val fullname: String,
    val teams: List<String>,
)

data class ProgramParticipantView(
    val id: String,
    val email: String,
    val fullname: String,
    val active: Boolean,
    val joinedAt: String,
    val teams: List<String>,
)

data class AdminProgramParticipantView(
    val id: String,
    val email: String,
    val fullname: String,
    val teams: List<String>,
    val active: Boolean,
    val joinedAt: String,
)

data class UpdateParticipantStatusRequest(val active: Boolean)

data class DeleteParticipantRequest(val confirmed: Boolean, val reason: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Event(
    val id: String,
    val name: String,
    val description: String,
    val startDate: String,
    val endDate: String,
    val location: String,
    val type: String,
    val externalEvent: Boolean = false,
    val deltaEvent: Boolean = true,
    val amountOfPeopleJoined: Int = 0
)