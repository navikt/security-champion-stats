package navikt.appsec.securitychampionapp.app.audit

import navikt.appsec.securitychampionapp.integrations.postgress.ProgramAuditRepository
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.CreditAwardResult
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.integrations.postgress.ScoringRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.UUID

class ProgramAuditServiceTest {
    @Test
    fun `should preserve the primary operation when best effort audit persistence fails`() {
        val repository = mock<ProgramAuditRepository>()
        whenever(repository.participantExists(UUID.fromString(PARTICIPANT_ID)))
            .thenThrow(IllegalStateException("audit database unavailable"))
        val service = ProgramAuditService(repository, Clock.systemUTC())

        assertThat(
            service.record(
                action = "ADMIN_MUTATION",
                outcome = AuditOutcome.FAILED,
                targetParticipantId = UUID.fromString(PARTICIPANT_ID),
            )
        ).isFalse()
    }

    @Test
    fun `should reject non-membership actions from participant history hooks`() {
        val repository = mock<ProgramAuditRepository>()
        val service = ProgramAuditService(repository, Clock.systemUTC())

        assertThat(service.recordParticipantEvent(UUID.fromString(PARTICIPANT_ID), "CREDIT_AWARDED")).isFalse()
    }

    @Test
    fun `should not change a successful credit award when the optional audit write fails`() {
        val scoringRepository = mock<ScoringRepository>()
        val auditRepository = mock<ProgramAuditRepository>()
        val participantId = UUID.fromString(PARTICIPANT_ID)
        val correlationId = UUID.randomUUID()
        whenever(
            scoringRepository.awardCredit(
                participantId,
                ActivityCreditType.SLACK_WEEK,
                "week",
                "channel:timestamp",
                correlationId,
            )
        ).thenReturn(CreditAwardResult.AWARDED)
        whenever(auditRepository.participantExists(participantId))
            .thenThrow(IllegalStateException("audit database unavailable"))
        val scoringService = ScoringService(
            scoringRepository,
            ProgramAuditService(auditRepository, Clock.systemUTC()),
        )

        val result = scoringService.awardCredit(
            participantId,
            ActivityCreditType.SLACK_WEEK,
            "week",
            "channel:timestamp",
            correlationId,
        )

        assertThat(result).isEqualTo(CreditAwardResult.AWARDED)
    }

    private companion object {
        const val PARTICIPANT_ID = "00000000-0000-0000-0000-000000000001"
    }
}
