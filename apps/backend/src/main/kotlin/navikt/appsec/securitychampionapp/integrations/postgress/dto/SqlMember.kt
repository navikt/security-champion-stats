package navikt.appsec.securitychampionapp.integrations.postgress.dto

data class SqlMember(
    val id: String,
    val fullname: String,
    val points: Int,
    val lastUpdated: String,
    val email: String,
    val inProgram: Boolean,
    val level: String,
    val teams: List<String>
)