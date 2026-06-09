package navikt.appsec.securitychampionapp.integrations.slack.dto

data class RemovedSecurityChampion(
    val email: String,
    val teamName: String,
    val fullName: String,
)
