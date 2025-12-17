package navikt.appsec.securitychampionstats.stats.dto

import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.serialization.Serializable

@JsonInclude(JsonInclude.Include.NON_NULL)
@Serializable
data class Member(val id: String, val fullname: String, val points: Int, val lastUpdated: String?, val email: String, val inProgram: Boolean = false)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Serializable
data class MemberInfo(val fullname: String, val id: String, val email: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Serializable
data class DeleteMember(val id: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Serializable
data class Points(val id: String, val points: Int)