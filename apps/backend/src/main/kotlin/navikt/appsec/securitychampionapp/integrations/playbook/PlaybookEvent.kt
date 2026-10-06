package navikt.appsec.securitychampionapp.integrations.playbook

data class PlaybookEvent(
    val id: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val audience: String,
    val url: String,
)
