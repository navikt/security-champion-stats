package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.postgress.SlackIdentityMappingRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackApiService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SlackScoringServiceTest {
    private val slackApiService = mock<SlackApiService>()
    private val mappingRepository = mock<SlackIdentityMappingRepository>()
    private val scoringService = mock<ScoringService>()
    private val service = SlackScoringService(slackApiService, mappingRepository, scoringService)

    @Test
    fun `should award at most one credit per participant week and retain unresolved authors`() {
        val participantId = UUID.randomUUID()
        val cursor = Instant.parse("2026-10-05T08:00:00Z")
        val now = cursor.plusSeconds(6 * 60 * 60)
        whenever(mappingRepository.syncCursor("channel", now)).thenReturn(cursor)
        whenever(mappingRepository.mappedParticipants()).thenReturn(
            mapOf(
                "U_ACTIVE" to MappedSlackParticipant(
                    slackUserId = "U_ACTIVE",
                    participantId = participantId,
                    active = true,
                    enrolledAt = cursor.minusSeconds(3600),
                ),
                "U_INACTIVE" to MappedSlackParticipant(
                    slackUserId = "U_INACTIVE",
                    participantId = UUID.randomUUID(),
                    active = false,
                    enrolledAt = cursor.minusSeconds(60),
                ),
                "U_NEW" to MappedSlackParticipant(
                    slackUserId = "U_NEW",
                    participantId = UUID.randomUUID(),
                    active = true,
                    enrolledAt = cursor.plusSeconds(30),
                ),
            )
        )
        whenever(slackApiService.fetchScoringMessages("channel", cursor.minus(Duration.ofDays(90)), now)).thenReturn(
            listOf(
                message("U_ACTIVE", "A qualifying root message with enough text", "2026-10-05T08:01:00Z"),
                message("U_ACTIVE", "A qualifying reply message with enough text", "2026-10-05T08:02:00Z"),
                message("U_NEW", "A pre-enrollment message with enough text", "2026-10-05T08:00:15Z"),
                message("U_INACTIVE", "An inactive user message with enough text", "2026-10-05T08:03:00Z"),
                message("U_UNKNOWN", "A qualifying unknown message with enough text", "2026-10-05T08:04:00Z"),
                message("U_BOT", "A qualifying bot message with enough text", "2026-10-05T08:05:00Z", botId = "B_BOT"),
                message("U_SYSTEM", "A system message with enough visible text", "2026-10-05T08:06:00Z", subtype = "channel_join"),
                message(
                    "U_ACTIVE",
                    "An edited message now has qualifying text",
                    "2026-10-05T07:59:00Z",
                    editedAt = "2026-10-05T08:07:00Z",
                ),
            )
        )
        whenever(
            scoringService.awardCredit(
                eq(participantId),
                eq(ActivityCreditType.SLACK_WEEK),
                eq("2026-10-05"),
                any(),
                anyOrNull(),
                anyOrNull(),
            )
        ).thenReturn(
            CreditAwardResult.AWARDED,
            CreditAwardResult.DUPLICATE,
            CreditAwardResult.DUPLICATE,
        )

        val summary = service.sync("channel", now)

        assertThat(summary.creditsAwarded).isEqualTo(1)
        assertThat(summary.duplicateCredits).isEqualTo(2)
        assertThat(summary.unmappedAuthors).isEqualTo(1)
        verify(mappingRepository).recordUnmappedAuthor("U_UNKNOWN")
        verify(mappingRepository, never()).recordUnmappedAuthor("U_BOT")
        verify(mappingRepository, never()).recordUnmappedAuthor("U_SYSTEM")
        verify(mappingRepository).advanceSyncCursor("channel", now)
    }

    @Test
    fun `should map unmapped authors by nav no email and award credit in the same sync`() {
        val participantId = UUID.randomUUID()
        val cursor = Instant.parse("2026-10-05T08:00:00Z")
        val now = cursor.plusSeconds(3600)
        val mapped = MappedSlackParticipant("U_PERSON", participantId, true, cursor.minusSeconds(86_400))
        whenever(mappingRepository.syncCursor("channel", now)).thenReturn(cursor)
        whenever(slackApiService.fetchScoringMessages("channel", cursor.minus(Duration.ofDays(90)), now)).thenReturn(
            listOf(
                message("U_PERSON", "A qualifying root message with enough text", "2026-10-05T08:01:00Z"),
                message("U_PERSON", "A qualifying reply message with enough text", "2026-10-05T08:02:00Z"),
                message("U_EXTERNAL", "A qualifying external message with enough text", "2026-10-05T08:03:00Z"),
                message("U_BOT", "A qualifying bot message with enough text", "2026-10-05T08:04:00Z", botId = "B1"),
            )
        )
        whenever(mappingRepository.mappedParticipants()).thenReturn(emptyMap(), mapOf("U_PERSON" to mapped))
        whenever(slackApiService.fetchUserEmail("U_PERSON")).thenReturn(" Person@NAV.no ")
        whenever(slackApiService.fetchUserEmail("U_EXTERNAL")).thenReturn("someone@nav.no.example.com")
        whenever(mappingRepository.addMappingByNavNoEmail("U_PERSON", "Person@NAV.no", "system:slack-email-match"))
            .thenReturn(true)
        whenever(
            scoringService.awardCredit(
                eq(participantId),
                eq(ActivityCreditType.SLACK_WEEK),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
            )
        )
            .thenReturn(CreditAwardResult.AWARDED, CreditAwardResult.DUPLICATE)

        val summary = service.sync("channel", now)

        assertThat(summary.creditsAwarded).isEqualTo(1)
        assertThat(summary.unmappedAuthors).isEqualTo(1)
        verify(slackApiService, times(1)).fetchUserEmail("U_PERSON")
        verify(slackApiService, never()).fetchUserEmail("U_BOT")
        verify(mappingRepository, never()).addMappingByNavNoEmail(eq("U_EXTERNAL"), any(), any())
        verify(mappingRepository).recordUnmappedAuthor("U_EXTERNAL")
    }

    @Test
    fun `should reject invalid Slack mapping identifiers`() {
        org.assertj.core.api.Assertions.assertThatThrownBy {
            service.addMapping(" ", UUID.randomUUID(), "admin@nav.no")
        }.isInstanceOf(InvalidScoringRequestException::class.java)
    }

    private fun message(
        userId: String,
        text: String,
        timestamp: String,
        botId: String? = null,
        subtype: String? = null,
        editedAt: String? = null,
    ) = SlackActivityMessage(
        channelId = "channel",
        userId = userId,
        text = text,
        timestamp = Instant.parse(timestamp),
        editedAt = editedAt?.let(Instant::parse),
        subtype = subtype,
        botId = botId,
    )
}
