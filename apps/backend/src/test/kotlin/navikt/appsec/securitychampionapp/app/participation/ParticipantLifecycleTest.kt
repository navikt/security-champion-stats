package navikt.appsec.securitychampionapp.app.participation

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ParticipantLifecycleTest {
    private val participants: ParticipantStore = mock()
    private val auditService: ProgramAuditService = mock()
    private val lifecycle = ParticipantLifecycle(
        participants = participants,
        profiles = ParticipantProfileSource { _, _ -> ParticipantProfileLookup.Unavailable },
        auditService = auditService,
    )

    @Test
    fun `should enroll with an incomplete profile when Teamkatalogen is unavailable`() {
        val participantId = UUID.randomUUID()
        whenever(participants.findByNavNoEmail("person@nav.no"))
            .thenReturn(null, participant(participantId))
        whenever(participants.enroll(any(), eq("person@nav.no"), eq("A12345"), eq("person@nav.no"), eq(""), eq(emptyList())))
            .thenReturn(1)

        val outcome = lifecycle.enroll("person@nav.no", "A12345", "person@nav.no")

        assertEquals(EnrollmentOutcome.ENROLLED, outcome)
        verify(participants).enroll(
            any(),
            eq("person@nav.no"),
            eq("A12345"),
            eq("person@nav.no"),
            eq(""),
            eq(emptyList()),
        )
        verify(auditService).recordParticipantEvent(
            participantId,
            "PARTICIPANT_ENROLLED",
            "person@nav.no",
            details = mapOf("status" to "ACTIVE"),
        )
    }

    private fun participant(id: UUID) =
        ProgramParticipant(
            id = id,
            navNoEmail = "person@nav.no",
            navIdent = "A12345",
            email = "person@nav.no",
            fullname = "",
            teams = emptyList(),
            status = ParticipationStatus.ACTIVE,
            createdAt = "2026-01-01T00:00:00Z",
        )
}
