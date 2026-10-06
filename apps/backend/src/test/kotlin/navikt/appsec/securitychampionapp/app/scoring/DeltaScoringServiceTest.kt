package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.delta.DeltaEventRegistrations
import navikt.appsec.securitychampionapp.integrations.delta.DeltaFailure
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import navikt.appsec.securitychampionapp.integrations.delta.DeltaRegistrationSource
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEligibleCategoryRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEventMappingRepository
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaScoringStatusRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipant
import navikt.appsec.securitychampionapp.integrations.postgress.dto.ProgramParticipantQueryResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
    private val categoryRepository = mock<DeltaEligibleCategoryRepository>()
    private val participantRepository = mock<ProgramParticipantRepository>()
    private val scoringService = mock<ScoringService>()
    private val eventSource = mock<DeltaRegistrationSource>()
    private val statusRepository = mock<DeltaScoringStatusRepository>()
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val service = DeltaScoringService(
        eventSource,
        mappingRepository,
        categoryRepository,
        participantRepository,
        scoringService,
        statusRepository,
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val participantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")

    @Test
    fun `should award past current-year events in an eligible category to registered participants`() {
        val eventId = uuid(1)
        whenever(categoryRepository.findAll()).thenReturn(listOf(category(7)))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        activeParticipants(participant(participantId, "participant@nav.no"))
        whenever(eventSource.pastEventsInCategory(7))
            .thenReturn(listOf(event(eventId, "2026-10-03T10:00:00", " PARTICIPANT@NAV.NO ", "other@nav.no")))
        awardReturns(eventId, CreditAwardResult.AWARDED)

        val summary = service.sync()

        assertThat(summary.eventsScanned).isEqualTo(1)
        assertThat(summary.creditsAwarded).isEqualTo(1)
        assertThat(summary.unmatchedRegistrations).isZero()
        verify(statusRepository).recordStarted(now)
        verify(statusRepository).recordSucceeded(now, summary)
    }

    @Test
    fun `should ignore events from previous years and events that have not started yet`() {
        val oldEventId = uuid(1)
        val laterTodayId = uuid(2)
        val futureEventId = uuid(3)
        whenever(categoryRepository.findAll()).thenReturn(listOf(category(7)))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        activeParticipants(participant(participantId, "participant@nav.no"))
        whenever(eventSource.pastEventsInCategory(7)).thenReturn(
            listOf(
                event(oldEventId, "2025-12-31T23:00:00", "participant@nav.no"),
                event(laterTodayId, "2026-10-05T14:00:00", "participant@nav.no"),
                event(futureEventId, "2026-11-01T10:00:00", "participant@nav.no"),
            )
        )

        val summary = service.sync()

        assertThat(summary.eventsScanned).isZero()
        verify(scoringService, never()).awardCredit(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `should award one event credit when host and participant emails differ only in case`() {
        val eventId = uuid(1)
        whenever(categoryRepository.findAll()).thenReturn(listOf(category(7)))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        activeParticipants(participant(participantId, "participant@nav.no"))
        whenever(eventSource.pastEventsInCategory(7))
            .thenReturn(listOf(event(eventId, "2026-10-03T10:00:00", "participant@nav.no", " PARTICIPANT@NAV.NO ")))
        awardReturns(eventId, CreditAwardResult.AWARDED)

        val summary = service.sync()

        assertThat(summary.creditsAwarded).isEqualTo(1)
        verify(scoringService).awardCredit(
            participantId,
            ActivityCreditType.DELTA_REGISTRATION,
            eventId.toString(),
            eventId.toString(),
        )
    }

    @Test
    fun `should fetch single event mappings that are not already covered by a category`() {
        val categoryEventId = uuid(1)
        val singleEventId = uuid(2)
        whenever(categoryRepository.findAll()).thenReturn(listOf(category(7)))
        whenever(mappingRepository.findAll()).thenReturn(listOf(mapping(categoryEventId), mapping(singleEventId)))
        activeParticipants(participant(participantId, "participant@nav.no"))
        whenever(eventSource.pastEventsInCategory(7))
            .thenReturn(listOf(event(categoryEventId, "2026-10-03T10:00:00", "participant@nav.no")))
        whenever(eventSource.event(singleEventId))
            .thenReturn(event(singleEventId, "2026-09-03T10:00:00", "participant@nav.no"))
        awardReturns(categoryEventId, CreditAwardResult.AWARDED)
        awardReturns(singleEventId, CreditAwardResult.DUPLICATE)

        val summary = service.sync()

        assertThat(summary.eventsScanned).isEqualTo(2)
        assertThat(summary.creditsAwarded).isEqualTo(1)
        assertThat(summary.duplicateCredits).isEqualTo(1)
        verify(eventSource, never()).event(categoryEventId)
    }

    @Test
    fun `should count ambiguous participant emails as unmatched`() {
        val eventId = uuid(1)
        whenever(categoryRepository.findAll()).thenReturn(listOf(category(7)))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        activeParticipants(
            participant(participantId, "participant@nav.no"),
            participant(uuid(9), "Participant@nav.no"),
        )
        whenever(eventSource.pastEventsInCategory(7))
            .thenReturn(listOf(event(eventId, "2026-10-03T10:00:00", "participant@nav.no")))

        val summary = service.sync()

        assertThat(summary.unmatchedRegistrations).isEqualTo(1)
        verify(scoringService, never()).awardCredit(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `should record partial failure when a source fails and continue with other sources`() {
        val eventId = uuid(1)
        val missingEventId = uuid(2)
        whenever(categoryRepository.findAll()).thenReturn(listOf(category(7), category(8)))
        whenever(mappingRepository.findAll()).thenReturn(listOf(mapping(missingEventId)))
        activeParticipants(participant(participantId, "participant@nav.no"))
        whenever(eventSource.pastEventsInCategory(7)).thenThrow(DeltaIntegrationException(DeltaFailure.API))
        whenever(eventSource.pastEventsInCategory(8))
            .thenReturn(listOf(event(eventId, "2026-10-03T10:00:00", "participant@nav.no")))
        whenever(eventSource.event(missingEventId)).thenReturn(null)
        awardReturns(eventId, CreditAwardResult.AWARDED)

        val summary = service.sync()

        assertThat(summary.failedEvents).isEqualTo(2)
        assertThat(summary.creditsAwarded).isEqualTo(1)
        assertThat(summary.failureSummary).isEqualTo("Some Delta categories or events could not be synchronized")
        verify(statusRepository).recordPartialFailure(now, summary)
    }

    @Test
    fun `should fail the sync when the Delta token cannot be acquired`() {
        whenever(categoryRepository.findAll()).thenReturn(listOf(category(7)))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        activeParticipants(participant(participantId, "participant@nav.no"))
        whenever(eventSource.pastEventsInCategory(7)).thenThrow(DeltaIntegrationException(DeltaFailure.TOKEN))

        assertThatThrownBy { service.sync() }.isInstanceOf(DeltaIntegrationException::class.java)
        verify(statusRepository).recordFailed(now, DeltaFailure.TOKEN.summary)
    }

    @Test
    fun `should record failure when the sync throws an unexpected exception`() {
        whenever(categoryRepository.findAll()).thenThrow(IllegalArgumentException("boom"))

        assertThatThrownBy { service.sync() }.isInstanceOf(IllegalArgumentException::class.java)
        verify(statusRepository).recordFailed(now, "Delta registration sync failed unexpectedly")
    }

    @Test
    fun `should do nothing when no categories or events are eligible`() {
        whenever(categoryRepository.findAll()).thenReturn(emptyList())
        whenever(mappingRepository.findAll()).thenReturn(emptyList())

        val summary = service.sync()

        assertThat(summary).isEqualTo(DeltaSyncSummary())
        verify(participantRepository, never()).findActiveParticipants()
    }

    private fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private fun activeParticipants(vararg participants: ProgramParticipant) {
        whenever(participantRepository.findActiveParticipants())
            .thenReturn(ProgramParticipantQueryResponse(true, participants.toList()))
    }

    private fun awardReturns(eventId: UUID, result: CreditAwardResult) {
        whenever(
            scoringService.awardCredit(
                participantId,
                ActivityCreditType.DELTA_REGISTRATION,
                eventId.toString(),
                eventId.toString(),
            )
        ).thenReturn(result)
    }

    private fun category(id: Int) = DeltaEligibleCategory(id, "Category $id", Instant.parse("2026-09-01T12:00:00Z"))

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

    private fun event(eventId: UUID, startTime: String, vararg emails: String) = DeltaEventRegistrations(
        eventUuid = eventId,
        startTime = LocalDateTime.parse(startTime),
        participantEmails = emails.toSet(),
    )
}
