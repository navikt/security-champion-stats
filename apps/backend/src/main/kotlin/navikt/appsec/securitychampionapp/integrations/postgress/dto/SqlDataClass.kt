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
    val teams: List<String>
)

data class DatabaseQueryResponse(
    val isOk: Boolean,
    val queryResult: List<SqlMember>? = null,
    val error: String? = null
)

data class DatabaseUpdateResponse(
    val isOk: Boolean,
    val error: String? = null
)