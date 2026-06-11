package navikt.appsec.securitychampionapp.utils
import org.springframework.beans.factory.annotation.Value

class PointsLogic(
    @Value($$"${pointsSystem.levels.novice}") val novice: Int,
    @Value($$"${pointsSystem.levels.apprentice}") val apprentice: Int,
    @Value($$"${pointsSystem.levels.adept}") val adept: Int,
    @Value($$"${pointsSystem.levels.expert}") val expert: Int
) {
    fun calculateLevel(points: Int): String {
        return when {
            points <= novice && points < apprentice -> "1"
            points in apprentice..<adept -> "2"
            points in adept..<expert -> "3"
            else -> "4"
        }

    }
}