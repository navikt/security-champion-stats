package navikt.appsec.securitychampionapp.app.jobs

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Component
class CalculatePointsJob {
    
    internal fun isOlderThanTwoDays(lastUpdated: String?, clock: Clock = Clock.systemUTC()): Boolean {
        val instant = lastUpdated
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { parseTimestamptzToInstant(it) }
            ?: return true

        val threshold = Instant.now(clock).minus(2, ChronoUnit.DAYS)
        return instant.isBefore(threshold)
    }

    private fun parseTimestamptzToInstant(text: String): Instant {
        runCatching {
            return Instant.parse(text)
        }

        val isoLike = text.replace(' ', 'T')
        return OffsetDateTime.parse(isoLike).toInstant()
    }
}
