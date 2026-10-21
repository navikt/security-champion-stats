package navikt.appsec.securitychampionapp.app.audit

import navikt.appsec.securitychampionapp.integrations.postgress.ProgramAuditRepository
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.AdminGroupMembers
import navikt.appsec.securitychampionapp.app.scoring.CreditAwardResult
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProgramAuditServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `should preserve the primary operation when best effort audit persistence fails`() {
        val repository = mock<ProgramAuditRepository>()
        whenever(repository.participantExists(UUID.fromString(PARTICIPANT_ID)))
            .thenThrow(IllegalStateException("audit database unavailable"))
        val service = ProgramAuditService(repository, clock)

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
        val service = ProgramAuditService(repository, clock)

        assertThat(service.recordParticipantEvent(UUID.fromString(PARTICIPANT_ID), "CREDIT_AWARDED")).isFalse()
    }

    @Test
    fun `should not change a successful credit award when the optional audit write fails`() {
        val scoringRepository = mock<ScoringLedger>()
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
            AdminGroupMembers { emptySet() },
            mock<PlatformTransactionManager>(),
            ProgramAuditService(auditRepository, clock),
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
