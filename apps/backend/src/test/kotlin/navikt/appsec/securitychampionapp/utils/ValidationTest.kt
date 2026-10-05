package navikt.appsec.securitychampionapp.utils

import org.junit.jupiter.api.Test

class ValidationTest {
    val validate = Validate()

    @Test
    fun `Given valid input should validate with true`() {
        val email = "local.test@nav.no"
        val result = validate.isValidEmail(email)
        assert(result)
    }

    @Test
    fun `Given invalid input should validate with false`() {
        val email = "; ' Select all from somewhere '"
        val result = validate.isValidEmail(email)
        assert(!result)
    }

    @Test
    fun `Given valid name should validate with true`() {
        val name = "Ola Nordmann"
        val result = validate.isValidName(name)
        assert(result)
    }

    @Test
    fun `Given value containing potential exploitable text should validate with false`() {
        val name = "Select * from somewhere"
        val result = validate.isValidName(name)
        assert(!result)
    }
}