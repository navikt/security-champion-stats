package navikt.appsec.securitychampionapp.app.jobs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CalculatePointsJobTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-03T12:00:00Z"), ZoneOffset.UTC)
    private val job = CalculatePointsJob()

    @Test
    fun `should treat missing last updated timestamp as older than two days`() {
        assertThat(job.isOlderThanTwoDays(null, fixedClock)).isTrue()
        assertThat(job.isOlderThanTwoDays("", fixedClock)).isTrue()
        assertThat(job.isOlderThanTwoDays("   ", fixedClock)).isTrue()
    }

    @Test
    fun `should identify timestamps older than two days`() {
        assertThat(job.isOlderThanTwoDays("2026-08-01T11:59:59Z", fixedClock)).isTrue()
    }

    @Test
    fun `should not identify timestamps exactly two days old as older`() {
        assertThat(job.isOlderThanTwoDays("2026-08-01T12:00:00Z", fixedClock)).isFalse()
    }

    @Test
    fun `should not identify recent timestamps as older than two days`() {
        assertThat(job.isOlderThanTwoDays("2026-08-02T12:00:00Z", fixedClock)).isFalse()
    }

    @Test
    fun `should parse postgres timestamptz values with a space separator`() {
        assertThat(job.isOlderThanTwoDays("2026-08-01 15:00:00+02:00", fixedClock)).isFalse()
        assertThat(job.isOlderThanTwoDays("2026-08-01 13:00:00+02:00", fixedClock)).isTrue()
    }
}
