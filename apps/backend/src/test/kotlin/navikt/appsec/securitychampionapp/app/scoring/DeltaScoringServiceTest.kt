package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventMatch
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
    private val yearStart = LocalDateTime.parse("2026-01-01T00:00:00")
    private val nextYearStart = LocalDateTime.parse("2027-01-01T00:00:00")

    @Test
    fun `should award a current-year registration to the participant queried from Delta`() {
        val eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val participantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
        whenever(mappingRepository.findAll()).thenReturn(listOf(mapping(eventId)))
        whenever(participantRepository.findActiveParticipants()).thenReturn(
            ProgramParticipantQueryResponse(true, listOf(participant(participantId, "participant@nav.no")))
        )
        whenever(eventSource.findRegisteredEvents(7, "participant@nav.no", yearStart, nextYearStart))
            .thenReturn(listOf(event(eventId, "2026-10-03T10:00:00")))
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
        verify(statusRepository).recordSucceeded(Instant.parse("2026-10-05T12:00:00Z"), summary)
        verify(scoringService).awardCredit(
            participantId,
            ActivityCreditType.DELTA_REGISTRATION,
            eventId.toString(),
            eventId.toString(),
        )
    }

    @Test
    fun `should normalize participant emails and ignore events outside the current year`() {
        val currentEventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val oldEventId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")
        val participantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
        whenever(mappingRepository.findAll()).thenReturn(
            listOf(mapping(currentEventId), mapping(oldEventId))
        )
        whenever(participantRepository.findActiveParticipants()).thenReturn(
            ProgramParticipantQueryResponse(true, listOf(participant(participantId, " PARTICIPANT@NAV.NO ")))
        )
        whenever(eventSource.findRegisteredEvents(7, "participant@nav.no", yearStart, nextYearStart))
            .thenReturn(
                listOf(
                    event(currentEventId, "2026-10-03T10:00:00"),
                    event(oldEventId, "2025-10-03T10:00:00"),
                )
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

        assertThat(summary.eventsScanned).isEqualTo(2)
        assertThat(summary.creditsAwarded).isEqualTo(1)
        assertThat(summary.unmatchedRegistrations).isZero()
        verify(eventSource).findRegisteredEvents(7, "participant@nav.no", yearStart, nextYearStart)
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
    fun `should record partial failure for a category and continue other categories`() {
        val failedEventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val successfulEventId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")
        val participantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
        whenever(mappingRepository.findAll()).thenReturn(
            listOf(mapping(failedEventId, 7), mapping(successfulEventId, 8))
        )
        whenever(participantRepository.findActiveParticipants()).thenReturn(
            ProgramParticipantQueryResponse(true, listOf(participant(participantId, "participant@nav.no")))
        )
        whenever(eventSource.findRegisteredEvents(7, "participant@nav.no", yearStart, nextYearStart))
            .thenThrow(DeltaIntegrationException(DeltaFailure.API))
        whenever(eventSource.findRegisteredEvents(8, "participant@nav.no", yearStart, nextYearStart))
            .thenReturn(listOf(event(successfulEventId, "2026-10-03T10:00:00")))
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
        verify(statusRepository).recordPartialFailure(Instant.parse("2026-10-05T12:00:00Z"), summary)
    }

    @Test
    fun `should mark legacy mappings without a category as incomplete`() {
        val eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        whenever(mappingRepository.findAll()).thenReturn(listOf(mapping(eventId, null)))

        val summary = service.sync()

        assertThat(summary.failedEvents).isEqualTo(1)
        assertThat(summary.failureSummary).isEqualTo(DeltaFailure.MAPPING_CATEGORY.summary)
        verify(participantRepository, never()).findActiveParticipants()
        verify(statusRepository).recordPartialFailure(Instant.parse("2026-10-05T12:00:00Z"), summary)
    }

    private fun mapping(eventId: UUID, categoryId: Int? = 7) = DeltaEventMapping(
        id = UUID.randomUUID(),
        programEventName = "Security meetup",
        deltaEventUuid = eventId,
        deltaCategoryId = categoryId,
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

    private fun event(eventId: UUID, startTime: String) = DeltaEventMatch(
        eventUuid = eventId,
        startTime = LocalDateTime.parse(startTime),
    )
}
