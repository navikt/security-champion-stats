package navikt.appsec.securitychampionapp.app.scoring

data class PointAdjustmentRequest(
    val pointsDelta: Int,
    val reason: String,
    val sourceCreditId: String? = null,
)

data class UpdateSeasonResetDateRequest(
    val nextResetDate: String,
)

data class ResetSeasonRequest(
    val confirmed: Boolean,
    val reason: String,
)
