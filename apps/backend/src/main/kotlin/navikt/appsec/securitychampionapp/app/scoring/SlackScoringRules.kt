package navikt.appsec.securitychampionapp.app.scoring

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

private val SLACK_SCORING_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

object SlackScoringRules {
    fun qualifies(text: String): Boolean {
        val trimmed = visibleText(text).trim()
        return trimmed.codePointCount(0, trimmed.length) >= 20 &&
            trimmed.codePoints().anyMatch { Character.isLetterOrDigit(it) }
    }

    fun weekStart(timestamp: Instant): LocalDate =
        timestamp.atZone(SLACK_SCORING_ZONE)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun visibleText(text: String): String {
        val links = text.replace(Regex("<[^<>|]+\\|([^<>]+)>"), "$1")
        val mentions = links
            .replace(Regex("<@[A-Z0-9]+(?:\\|[^>]+)?>"), "@")
            .replace(Regex("<#[A-Z0-9]+\\|([^>]+)>"), "$1")
            .replace(Regex("<#[A-Z0-9]+>"), "#")
            .replace(Regex("<!([a-z]+)>", RegexOption.IGNORE_CASE), "@$1")
            .replace(Regex(":[a-z0-9_+-]+:", RegexOption.IGNORE_CASE), "🙂")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        val formatted = Regex("([*_~`])(.+?)\\1").replace(mentions) { it.groupValues[2] }
        return Regex("(?m)^> ?").replace(formatted, "")
    }
}
