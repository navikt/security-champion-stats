package navikt.appsec.securitychampionapp.app.membership

import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.app.participation.ProgramParticipant
import navikt.appsec.securitychampionapp.config.TeamCatalogRoleProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class TeamCatalogRoleServiceTest {
    private val store: ParticipantStore = mock()
    private val management: TeamCatalogRoleManagement = mock()

    @Test
    fun `active members can preview and save their current team roles`() {
        whenever(store.findByNavNoEmail("person@nav.no")).thenReturn(participant(ParticipationStatus.ACTIVE))
        whenever(management.memberships("person@nav.no", "A12345"))
            .thenReturn(listOf(TeamCatalogTeamMembership("team-1", "Alpha", true)))
        whenever(management.saveSelection("person@nav.no", "A12345", setOf("team-1"), false))
            .thenReturn(TeamCatalogRoleSaveResult(
                teams = listOf(TeamCatalogTeamMembership("team-1", "Alpha", true)),
                updates = emptyList(),
                writeEnabled = true,
                dryRun = false,
            ))
        val service = TeamCatalogRoleService(
            store,
            management,
            ChampionRoleSource { emptyMap() },
            TeamCatalogRoleProperties(enabled = true, dryRun = true),
        )

        assertThat(service.currentTeams("person@nav.no", "A12345").dryRun).isTrue()
        val result = service.saveSelection("person@nav.no", "A12345", setOf("team-1"))

        assertThat(result.writeEnabled).isFalse()
        assertThat(result.dryRun).isTrue()
    }

    @Test
    fun `inactive members cannot manage team roles`() {
        whenever(store.findByNavNoEmail("person@nav.no")).thenReturn(participant(ParticipationStatus.LEFT))
        val service = TeamCatalogRoleService(
            store,
            management,
            ChampionRoleSource { emptyMap() },
            TeamCatalogRoleProperties(),
        )

        assertThatThrownBy { service.currentTeams("person@nav.no", "A12345") }
            .isInstanceOf(TeamCatalogRoleAccessException::class.java)
    }

    @Test
    fun `admin status distinguishes confirmed missing roles from unknown identities`() {
        val active = listOf(
            participant(ParticipationStatus.ACTIVE),
            participant(ParticipationStatus.ACTIVE, id = UUID.fromString("00000000-0000-0000-0000-000000000002"), email = "unknown@nav.no"),
        )
        whenever(store.findActiveParticipants()).thenReturn(active)
        val service = TeamCatalogRoleService(
            store,
            management,
            ChampionRoleSource { mapOf("person@nav.no" to ChampionRole.ABSENT) },
            TeamCatalogRoleProperties(),
        )

        assertThat(service.championStatus().participants.map { it.status })
            .containsExactly(ChampionRole.ABSENT, ChampionRole.UNKNOWN)
    }

    private fun participant(
        status: ParticipationStatus,
        id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        email: String = "person@nav.no",
    ) = ProgramParticipant(
        id = id,
        navNoEmail = email,
        navIdent = "A12345",
        email = email,
        fullname = "Person",
        teams = emptyList(),
        status = status,
        createdAt = "2026-01-01T00:00:00Z",
    )
}
