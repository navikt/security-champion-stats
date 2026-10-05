package navikt.appsec.securitychampionapp.app.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SlackScoringRulesTest {
    @Test
    fun `should count Unicode code points after trimming`() {
        assertThat(SlackScoringRules.qualifies("  ${"😀".repeat(19)}a  ")).isTrue()
        assertThat(SlackScoringRules.qualifies("ø".repeat(20))).isTrue()
        assertThat(SlackScoringRules.qualifies("!".repeat(20))).isFalse()
        assertThat(SlackScoringRules.qualifies("word".repeat(4))).isFalse()
        assertThat(SlackScoringRules.qualifies(":smile:".repeat(18) + "a")).isFalse()
        assertThat(SlackScoringRules.qualifies("<https://example.com|go>" + "a".repeat(17))).isFalse()
        assertThat(SlackScoringRules.qualifies("<https://example.com|go>" + "a".repeat(18))).isTrue()
    }

    @Test
    fun `should assign messages to Monday to Sunday weeks in Oslo`() {
        assertThat(SlackScoringRules.weekStart(Instant.parse("2026-10-04T21:59:59Z")))
            .isEqualTo(LocalDate.parse("2026-09-28"))
        assertThat(SlackScoringRules.weekStart(Instant.parse("2026-10-04T22:00:00Z")))
            .isEqualTo(LocalDate.parse("2026-10-05"))
    }
}
