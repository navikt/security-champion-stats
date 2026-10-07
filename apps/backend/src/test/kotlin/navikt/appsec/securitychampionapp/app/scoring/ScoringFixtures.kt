package navikt.appsec.securitychampionapp.app.scoring

val defaultScoringConfiguration = ScoringConfiguration(
    1,
    listOf(ScoringTier("Novice", 0), ScoringTier("Apprentice", 100), ScoringTier("Adept", 250), ScoringTier("Expert", 500)),
    ActivityCreditType.entries.map {
        ActivityPoints(it, if (it in setOf(ActivityCreditType.GITHUB_PULL_REQUEST, ActivityCreditType.SECURITY_EVENT_CONTRIBUTION)) 3 else 1)
    },
)
