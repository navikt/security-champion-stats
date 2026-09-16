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
    val joinedAt: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AddMember(val fullName: String, val email: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InviteRequest(val fullName: String, val email: String, val requesterEmail: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InviteResponse(val status: String, val notice: String? = null)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Points(val email: String, val points: Int)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChallengeMember(
    val id: String,
    val fullname: String,
    val email: String,
    val points: Int = 0,
    val level: String = "1",
    val inProgram: Boolean = false
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SCdata(val timestamp: String, val amount: Int)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AppSecDashboard(val data: List<SCdata>, val notice: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Me(val username: String, val isAdmin: Boolean, val isSecChamp: Boolean, val inGame: Boolean)