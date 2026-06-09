package navikt.appsec.securitychampionapp.integrations.slack.dto

data class NewSecurityChampion(
    val email: String,
    val teamNames: List<String>,
    val fullName: String,
)
