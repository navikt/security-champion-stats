package navikt.appsec.securitychampionapp.utils

import org.springframework.stereotype.Component

@Component
class Validate {
    fun isValidEmail(email: String): Boolean {
        return "^[A-Za-z0-9+_.-]+@nav.no".toRegex().containsMatchIn(email)
    }
    fun isValidName(name: String): Boolean {
        return "^[a-zA-ZæøåÆØÅ\\s]+$".toRegex().containsMatchIn(name)
    }
}