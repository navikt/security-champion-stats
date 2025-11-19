package navikt.appsec.securitychampionstats.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class MemberDTO(val id: String, val fullname: String, val points: Int, val email: String)
@Serializable
data class PointsDTO(val id: String, val points: Int)