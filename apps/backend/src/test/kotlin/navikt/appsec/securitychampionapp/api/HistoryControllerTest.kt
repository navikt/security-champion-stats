package navikt.appsec.securitychampionapp.api

import navikt.appsec.securitychampionapp.app.api.HistoryController
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.app.scoring.ParticipantScoreHistoryEntry
import navikt.appsec.securitychampionapp.app.scoring.ScoreHistoryPage
import navikt.appsec.securitychampionapp.app.scoring.ScoringService
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class HistoryControllerTest {
    private val participantRepository = mock<ParticipantStore>()
    private val scoringService = mock<ScoringService>()
    private val controller = HistoryController(participantRepository, scoringService)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should resolve privacy-safe history from the authenticated identity including inactive participants`() {
        val participantId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            AppPrincipal("signed-in@nav.no", "A12345"),
            null,
            emptyList(),
        )
        whenever(participantRepository.findByNavNoEmail("signed-in@nav.no")).thenReturn(
            ProgramParticipant(
                id = participantId,
                navNoEmail = "signed-in@nav.no",
                navIdent = "A12345",
                email = "signed-in@nav.no",
                fullname = "Participant",
                teams = emptyList(),
                status = ParticipationStatus.DEACTIVATED,
                createdAt = "2026-01-01T00:00:00Z",
            )
        )
        val expected = ScoreHistoryPage(
            entries = listOf(
                ParticipantScoreHistoryEntry(
                    kind = "credit",
                    occurredAt = java.time.Instant.parse("2026-10-01T00:00:00Z"),
                    creditType = navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType.GITHUB_COMMIT,
                    points = 1,
                    displayName = null,
                    action = null,
                ),
            ),
            nextCursor = null,
        )
        whenever(scoringService.participantScoreHistoryPage(participantId, null, null, null, null, false))
            .thenReturn(expected)

        val response = controller.history()

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo(expected)
        verify(participantRepository).findByNavNoEmail("signed-in@nav.no")
        verify(scoringService).participantScoreHistoryPage(participantId, null, null, null, null, false)
    }
}
