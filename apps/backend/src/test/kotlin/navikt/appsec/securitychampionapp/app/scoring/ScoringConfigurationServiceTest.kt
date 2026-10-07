package navikt.appsec.securitychampionapp.app.scoring

import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.UUID
import java.util.stream.Stream

class ScoringConfigurationServiceTest {
    private val ledger = mock<ScoringLedger>()
    private val service = ScoringConfigurationService(ledger)

    @ParameterizedTest
    @MethodSource("invalidRequests")
    fun `should reject invalid configuration before accessing persistence`(request: ScoringConfigurationRequest) {
        assertThrows(InvalidScoringRequestException::class.java) { service.preview(request) }
        verifyNoInteractions(ledger)
    }

    @Test
    fun `should normalize names thresholds and activity ordering before previewing`() {
        val request = validRequest.copy(
            tiers = listOf(ScoringTier(" Champion ", 10), ScoringTier(" Starter ", 0)),
            activities = validRequest.activities.reversed(),
            reason = " Balance scoring ",
        )
        val normalized = request.copy(
            tiers = listOf(ScoringTier("Starter", 0), ScoringTier("Champion", 10)),
            activities = validRequest.activities,
            reason = "Balance scoring",
        )
        val season = SeasonSummary(UUID.randomUUID(), LocalDate.of(2026, 1, 1), null, LocalDate.of(2027, 1, 1))
        whenever(ledger.previewConfiguration(normalized)).thenReturn(ScoringConfigurationPreview("token", season, 0, 0, emptyList()))
        service.preview(request)
        verify(ledger).previewConfiguration(normalized)
    }

    @Test
    fun `should require preview confirmation and administrator identity before saving`() {
        assertThrows(InvalidScoringRequestException::class.java) { service.save(validRequest, "admin@nav.no") }
        assertThrows(InvalidScoringRequestException::class.java) {
            service.save(validRequest.copy(previewToken = "token"), " ")
        }
        verifyNoInteractions(ledger)
    }

    companion object {
        private val validRequest = ScoringConfigurationRequest(
            1, defaultScoringConfiguration.tiers, defaultScoringConfiguration.activities, reason = "Balance scoring",
        )

        @JvmStatic
        fun invalidRequests(): Stream<ScoringConfigurationRequest> = Stream.of(
            validRequest.copy(tiers = emptyList()),
            validRequest.copy(tiers = listOf(ScoringTier("Starter", 1))),
            validRequest.copy(tiers = listOf(ScoringTier("Starter", 0), ScoringTier("Champion", 0))),
            validRequest.copy(tiers = listOf(ScoringTier("Starter", 0), ScoringTier(" starter ", 5))),
            validRequest.copy(tiers = listOf(ScoringTier(" ", 0))),
            validRequest.copy(tiers = listOf(ScoringTier("x".repeat(81), 0))),
            validRequest.copy(tiers = listOf(ScoringTier("Starter", 0), ScoringTier("Champion", -1))),
            validRequest.copy(activities = validRequest.activities.drop(1)),
            validRequest.copy(activities = validRequest.activities + validRequest.activities.first()),
            validRequest.copy(activities = validRequest.activities.map { it.copy(points = -1) }),
            validRequest.copy(reason = " "),
            validRequest.copy(reason = "x".repeat(1001)),
        )
    }
}
