package navikt.appsec.securitychampionstats.utils

class Validate {
    fun isValidEmail(email: String): Boolean {
        return "^[A-Za-z0-9+_.-]+@nav.no".toRegex().containsMatchIn(email)
    }
    fun isValidNumber(number: String): Boolean {
        return "^[0-9]+$".toRegex().containsMatchIn(number)
    }
    fun isValidName(name: String): Boolean {
        return "^[a-zA-ZæøåÆØÅ\\s]+$".toRegex().containsMatchIn(name)
    }
}