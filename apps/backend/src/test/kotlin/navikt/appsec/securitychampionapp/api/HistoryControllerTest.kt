package navikt.appsec.securitychampionapp.api

import navikt.appsec.securitychampionapp.app.api.HistoryController
import navikt.appsec.securitychampionapp.app.audit.ParticipantHistoryEntry
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantQueryResponse
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

class HistoryControllerTest {
    private val participantRepository = mock<ProgramParticipantRepository>()
    private val auditService = mock<ProgramAuditService>()
    private val controller = HistoryController(participantRepository, auditService)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should resolve history from the authenticated identity including inactive participants`() {
        val participantId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            AppPrincipal("signed-in@nav.no", "A12345"),
            null,
            emptyList(),
        )
        whenever(participantRepository.findByNavNoEmail("signed-in@nav.no")).thenReturn(
            ProgramParticipantQueryResponse(
                isOk = true,
                queryResult = listOf(
                    ProgramParticipant(
                        id = participantId.toString(),
                        navNoEmail = "signed-in@nav.no",
                        navIdent = "A12345",
                        email = "signed-in@nav.no",
                        fullname = "Participant",
                        teams = emptyList(),
                        status = "DEACTIVATED",
                        createdAt = "2026-01-01T00:00:00Z",
                    )
                ),
            )
        )
        val expected = listOf(
            ParticipantHistoryEntry(
                id = "credit-id",
                occurredAt = Instant.parse("2026-10-01T00:00:00Z"),
                type = "CREDIT",
                action = "CREDIT_AWARDED",
                status = null,
                creditType = "GITHUB_COMMIT",
                points = 1,
                sourceReference = "commit:42",
                reason = null,
            )
        )
        whenever(auditService.participantHistory(participantId)).thenReturn(expected)

        val response = controller.history()

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo(expected)
        verify(participantRepository).findByNavNoEmail("signed-in@nav.no")
        verify(auditService).participantHistory(eq(participantId))
    }
}
