package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.dao.DataAccessResourceFailureException
import java.util.UUID

class SlackIdentityProvisioningServiceTest {
    private val participants = mock<ParticipantStore>()
    private val identities = mock<SlackIdentityStore>()
    private val lookup = mock<SlackParticipantLookup>()
    private val service = SlackIdentityProvisioningService(participants, identities, lookup)
    private val participant = ProgramParticipant(
        UUID.randomUUID(), "synthetic@nav.no", null, "synthetic@nav.no", "Synthetic", emptyList(),
        ParticipationStatus.ACTIVE, "2026-10-01",
    )

    init {
        whenever(identities.mappedParticipantIds()).thenReturn(emptySet())
        whenever(participants.findById(participant.id)).thenReturn(participant)
        whenever(participants.findActiveParticipants()).thenReturn(listOf(participant))
    }

    @Test
    fun `new enrollment resolves the verified participant email and persists the mapping`() {
        whenever(lookup.findEligibleUser(participant.navNoEmail)).thenReturn("U_SYNTHETIC")
        whenever(identities.saveVerifiedMapping("U_SYNTHETIC", participant)).thenReturn(true)
        assertThat(service.provision(participant.id)).isEqualTo(SlackIdentityProvisioningSummary(1, 0, 0))
        verify(lookup).findEligibleUser(participant.navNoEmail)
        verify(identities).saveVerifiedMapping("U_SYNTHETIC", participant)
    }

    @Test
    fun `administrator mappings are preserved and inactive or deleted participants are never looked up`() {
        whenever(identities.mappedParticipantIds()).thenReturn(setOf(participant.id))
        assertThat(service.provision(participant.id)).isEqualTo(SlackIdentityProvisioningSummary(0, 0, 1))
        whenever(identities.mappedParticipantIds()).thenReturn(emptySet())
        whenever(participants.findById(participant.id)).thenReturn(participant.copy(status = ParticipationStatus.LEFT), null)
        assertThat(service.provision(participant.id)).isEqualTo(SlackIdentityProvisioningSummary(0, 0, 1))
        assertThat(service.provision(participant.id)).isEqualTo(SlackIdentityProvisioningSummary(0, 0, 0))
        verifyNoInteractions(lookup)
        verify(identities, never()).saveVerifiedMapping(any(), any())
    }

    @Test
    fun `scheduled reconciliation backfills existing participants and retries unresolved identities`() {
        whenever(lookup.findEligibleUser(participant.navNoEmail)).thenReturn(null, "U_SYNTHETIC")
        assertThat(service.provision()).isEqualTo(SlackIdentityProvisioningSummary(0, 1, 0))
        whenever(identities.saveVerifiedMapping("U_SYNTHETIC", participant)).thenReturn(true)
        assertThat(service.provision()).isEqualTo(SlackIdentityProvisioningSummary(1, 0, 0))
    }

    @Test
    fun `conflicting identities remain unresolved instead of overwriting a mapping`() {
        whenever(lookup.findEligibleUser(participant.navNoEmail)).thenReturn("U_CONFLICT")
        whenever(identities.saveVerifiedMapping("U_CONFLICT", participant)).thenReturn(false)
        assertThat(service.provision()).isEqualTo(SlackIdentityProvisioningSummary(0, 1, 0))
    }

    @Test
    fun `concurrent departure prevents persistence and does not count as a verified mapping`() {
        whenever(lookup.findEligibleUser(participant.navNoEmail)).thenReturn("U_SYNTHETIC")
        whenever(participants.findById(participant.id)).thenReturn(participant, null)
        assertThat(service.provision(participant.id)).isEqualTo(SlackIdentityProvisioningSummary(0, 0, 1))
    }

    @Test
    fun `Slack and database failures propagate to the job error boundary`() {
        whenever(lookup.findEligibleUser(any())).thenThrow(IllegalStateException("Slack unavailable"))
        assertThatThrownBy { service.provision() }.isInstanceOf(IllegalStateException::class.java)
        whenever(lookup.findEligibleUser(any())).thenReturn("U_SYNTHETIC")
        whenever(identities.saveVerifiedMapping(any(), any())).thenThrow(DataAccessResourceFailureException("Database unavailable"))
        assertThatThrownBy { service.provision() }.isInstanceOf(DataAccessResourceFailureException::class.java)
    }
}
