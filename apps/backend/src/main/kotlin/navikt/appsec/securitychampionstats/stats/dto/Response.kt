package navikt.appsec.securitychampionstats.stats.dto

import com.fasterxml.jackson.annotation.JsonInclude
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Member(val id: String, val fullname: String, val points: Int, val lastUpdated: String?)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemberInfo(val fullname: String)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeleteMember(val id: String)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Points(val id: String, val points: Int)