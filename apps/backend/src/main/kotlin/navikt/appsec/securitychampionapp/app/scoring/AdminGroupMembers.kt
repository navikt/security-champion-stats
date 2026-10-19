package navikt.appsec.securitychampionapp.app.scoring

fun interface AdminGroupMembers {
    fun emails(): Set<String>
}

class AdminGroupUnavailableException : RuntimeException("Administrator group membership could not be verified")
