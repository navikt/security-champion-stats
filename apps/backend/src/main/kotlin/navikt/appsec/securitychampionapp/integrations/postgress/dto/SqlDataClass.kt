package navikt.appsec.securitychampionapp.integrations.postgress.dto

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

enum class EventType {
    WORKSHOP, MEETUP
}