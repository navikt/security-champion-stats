package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventRoster
import navikt.appsec.securitychampionapp.integrations.delta.DeltaFailure
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import navikt.appsec.securitychampionapp.integrations.delta.DeltaRegistrationSource
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEventMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantQueryResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class DeltaScoringServiceTest {
    private val mappingRepository = mock<DeltaEventMappingRepository>()
    private val participantRepository = mock<ProgramParticipantRepository>()
    private val scoringService = mock<ScoringService>()
    private val eventSource = mock<DeltaRegistrationSource>()
    private val statusRepository = mock<DeltaScoringStatusRepository>()
    private val service = DeltaScoringService(
        eventSource,
        mappingRepository,
        participantRepository,
        scoringService,
        statusRepository,
        Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `should award one current-year registration credit to an active matching participant`() {
        val eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val participantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
        whenever(mappingRepository.findAll()).thenReturn(
            listOf(
                DeltaEventMapping(
                    id = UUID.randomUUID(),
                    programEventName = "Security meetup",
                    deltaEventUuid = eventId,
                    createdAt = Instant.parse("2026-09-01T12:00:00Z"),
                )
            )
        )
        whenever(participantRepository.findActiveParticipants()).thenReturn(
            ProgramParticipantQueryResponse(
                isOk = true,
                queryResult = listOf(
                    ProgramParticipant(
                        id = participantId.toString(),
                        navNoEmail = "participant@nav.no",
                        navIdent = null,
                        email = "participant@nav.no",
                        fullname = "Synthetic Participant",
                        teams = emptyList(),
                        status = "ACTIVE",
                        createdAt = "2026-09-01T12:00:00Z",
                    )
                )
            )
        )
        whenever(eventSource.fetchEvent(eventId)).thenReturn(
            DeltaEventRoster(
                eventUuid = eventId,
                startTime = LocalDateTime.parse("2026-10-03T10:00:00"),
                participantEmails = listOf("participant@nav.no"),
            )
        )
        whenever(
            scoringService.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                eventId.toString(),
                eventId.toString(),
            )
        ).thenReturn(CreditAwardResult.AWARDED)

        val summary = service.sync()

        assertThat(summary.creditsAwarded).isEqualTo(1)
        verify(statusRepository).recordStarted(Instant.parse("2026-10-05T12:00:00Z"))
        verify(statusRepository).recordSucceeded(
            Instant.parse("2026-10-05T12:00:00Z"),
            summary,
        )
        verify(scoringService).awardCredit(
            participantId,
            ActivityCreditType.DELTA_REGISTRATION,
            eventId.toString(),
            eventId.toString(),
        )
    }

    @Test
    fun `should ignore old events and normalize registration emails`() {
        val currentEventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val oldEventId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")
        val participantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
        whenever(mappingRepository.findAll()).thenReturn(
            listOf(mapping(currentEventId), mapping(oldEventId))
        )
        whenever(participantRepository.findActiveParticipants()).thenReturn(
            ProgramParticipantQueryResponse(true, listOf(participant(participantId, "participant@nav.no")))
        )
        whenever(eventSource.fetchEvent(currentEventId)).thenReturn(
            roster(currentEventId, "2026-10-03T10:00:00", " Participant@NAV.NO ", "participant@nav.no")
        )
        whenever(eventSource.fetchEvent(oldEventId)).thenReturn(
            roster(oldEventId, "2025-10-03T10:00:00", "participant@nav.no")
        )
        whenever(
            scoringService.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                currentEventId.toString(),
                currentEventId.toString(),
            )
        ).thenReturn(CreditAwardResult.AWARDED)

        val summary = service.sync()

        assertThat(summary.eventsScanned).isEqualTo(1)
        assertThat(summary.creditsAwarded).isEqualTo(1)
        assertThat(summary.unmatchedRegistrations).isZero()
        verify(scoringService).awardCredit(
            participantId,
            ActivityCreditType.DELTA_REGISTRATION,
            currentEventId.toString(),
            currentEventId.toString(),
        )
        verify(scoringService, never()).awardCredit(
            participantId,
            ActivityCreditType.DELTA_REGISTRATION,
            oldEventId.toString(),
            oldEventId.toString(),
        )
    }

    @Test
    fun `should retain failure status when one mapped Delta event cannot be fetched`() {
        val failedEventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val successfulEventId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")
        val participantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
        whenever(mappingRepository.findAll()).thenReturn(
            listOf(mapping(failedEventId), mapping(successfulEventId))
        )
        whenever(participantRepository.findActiveParticipants()).thenReturn(
            ProgramParticipantQueryResponse(true, listOf(participant(participantId, "participant@nav.no")))
        )
        whenever(eventSource.fetchEvent(failedEventId)).thenThrow(DeltaIntegrationException(DeltaFailure.API))
        whenever(eventSource.fetchEvent(successfulEventId)).thenReturn(
            roster(successfulEventId, "2026-10-03T10:00:00", "participant@nav.no")
        )
        whenever(
            scoringService.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                successfulEventId.toString(),
                successfulEventId.toString(),
            )
        ).thenReturn(CreditAwardResult.AWARDED)

        val summary = service.sync()

        assertThat(summary.failedEvents).isEqualTo(1)
        assertThat(summary.eventsScanned).isEqualTo(1)
        assertThat(summary.creditsAwarded).isEqualTo(1)
        verify(statusRepository).recordStarted(Instant.parse("2026-10-05T12:00:00Z"))
        verify(statusRepository).recordPartialFailure(
            Instant.parse("2026-10-05T12:00:00Z"),
            summary,
        )
    }

    private fun mapping(eventId: UUID) = DeltaEventMapping(
        id = UUID.randomUUID(),
        programEventName = "Security meetup",
        deltaEventUuid = eventId,
        createdAt = Instant.parse("2026-09-01T12:00:00Z"),
    )

    private fun participant(id: UUID, email: String) = ProgramParticipant(
        id = id.toString(),
        navNoEmail = email,
        navIdent = null,
        email = email,
        fullname = "Synthetic Participant",
        teams = emptyList(),
        status = "ACTIVE",
        createdAt = "2026-09-01T12:00:00Z",
    )

    private fun roster(eventId: UUID, startTime: String, vararg emails: String) = DeltaEventRoster(
        eventUuid = eventId,
        startTime = LocalDateTime.parse(startTime),
        participantEmails = emails.toList(),
    )
}
