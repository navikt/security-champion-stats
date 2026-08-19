package navikt.appsec.securitychampionapp.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class Validate(
    @Value($$"${pointsSystem.levels.novice}") val novice: Int,
    @Value($$"${pointsSystem.levels.apprentice}") val apprentice: Int,
    @Value($$"${pointsSystem.levels.adept}") val adept: Int,
    @Value($$"${pointsSystem.levels.expert}") val expert: Int
) {
    fun isValidEmail(email: String): Boolean {
        return "^[A-Za-z0-9+_.-]+@nav.no".toRegex().containsMatchIn(email)
    }
    fun isValidNumber(number: String): Boolean {
        return "^[0-9]+$".toRegex().containsMatchIn(number)
    }
    fun isValidName(name: String): Boolean {
        return "^[a-zA-ZæøåÆØÅ\\s]+$".toRegex().containsMatchIn(name)
    }

    fun calculateLevel(points: Int): String {
        return when {
            points <= novice && points < apprentice -> "1"
            points in apprentice..<adept -> "2"
            points in adept..<expert -> "3"
            else -> "4"
        }
    }
}