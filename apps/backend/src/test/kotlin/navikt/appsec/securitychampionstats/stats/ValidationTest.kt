package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.utils.Validate
import org.junit.jupiter.api.Test


class ValidationTest {

    @Test
    fun `Given valid input should validate with true`() {
        val email = "local.test@nav.no"
        val result = Validate().isValidEmail(email)
        assert(result)
    }

    @Test
    fun `Given invalid input should validate with false`() {
        val email = "; ' Select all from somewhere '"
        val result = Validate().isValidEmail(email)
        assert(!result)
    }

    @Test
    fun `Given valid number should validate with true`() {
        val number = 1234567890
        val result = Validate().isValidNumber(number.toString())
        assert(result)
    }

    @Test
    fun `Given invalid number should validate with false`() {
        val number = "1234abc567890"
        val result = Validate().isValidNumber(number)
        assert(!result)
    }

    @Test
    fun `Given valid name should validate with true`() {
        val name = "Ola Nordmann"
        val result = Validate().isValidName(name)
        assert(result)
    }

    @Test
    fun `Given value containing potential exploitable text should validate with false`() {
        val name = "Select * from somewhere"
        val result = Validate().isValidName(name)
        assert(!result)
    }
}