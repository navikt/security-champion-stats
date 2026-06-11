package navikt.appsec.securitychampionapp.app.api.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Member(
    val id: String,
    val fullname: String,
    val points: Int,
    val lastUpdated: String?,
    val email: String,
    val inProgram: Boolean = false,
    val level: String = "1"
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberInfo(val fullname: String, val email: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Points(val email: String, val points: Int)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SCdata(val timestamp: String, val amount: Int)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Me(val username: String, val isAdmin: Boolean, val inProgram: Boolean)