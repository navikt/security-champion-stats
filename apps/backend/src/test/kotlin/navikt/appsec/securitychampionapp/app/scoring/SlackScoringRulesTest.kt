package navikt.appsec.securitychampionapp.app.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SlackScoringRulesTest {
    @Test
    fun `should count Unicode code points after trimming`() {
        assertThat(SlackScoringRules.qualifies("  ${"😀".repeat(4)}a  ")).isTrue()
        assertThat(SlackScoringRules.qualifies("ø".repeat(5))).isTrue()
        assertThat(SlackScoringRules.qualifies("!".repeat(5))).isFalse()
        assertThat(SlackScoringRules.qualifies("word")).isFalse()
        assertThat(SlackScoringRules.qualifies("Ja!")).isFalse()
        assertThat(SlackScoringRules.qualifies(":smile:".repeat(3) + "a")).isFalse()
        assertThat(SlackScoringRules.qualifies(":smile:".repeat(4) + "a")).isTrue()
        assertThat(SlackScoringRules.qualifies("<https://example.com|go>" + "a".repeat(2))).isFalse()
        assertThat(SlackScoringRules.qualifies("<https://example.com|go>" + "a".repeat(3))).isTrue()
        assertThat(SlackScoringRules.qualifies("Jeg er en sopp :security-champignon:")).isTrue()
    }

    @Test
    fun `should assign messages to Monday to Sunday weeks in Oslo`() {
        assertThat(SlackScoringRules.weekStart(Instant.parse("2026-10-04T21:59:59Z")))
            .isEqualTo(LocalDate.parse("2026-09-28"))
        assertThat(SlackScoringRules.weekStart(Instant.parse("2026-10-04T22:00:00Z")))
            .isEqualTo(LocalDate.parse("2026-10-05"))
    }
}
