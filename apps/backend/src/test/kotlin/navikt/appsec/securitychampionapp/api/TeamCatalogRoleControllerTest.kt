package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.TeamCatalogRoleController
import navikt.appsec.securitychampionapp.app.membership.ChampionRole
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogChampionStatus
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogChampionStatusOverview
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleSaveResult
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleService
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleState
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogTeamMembership
import navikt.appsec.securitychampionapp.config.ADMIN_ROLE
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.config.USER_ROLE
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(TeamCatalogRoleController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class TeamCatalogRoleControllerTest {
    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var service: TeamCatalogRoleService

    @MockitoBean
    lateinit var filter: AppAuthenticationFilter

    @Test
    fun `member sees current roles without receiving the outbound team payload`() {
        authenticated(USER_ROLE)
        whenever(service.currentTeams("person@nav.no", "A12345")).thenReturn(
            TeamCatalogRoleState(
                teams = listOf(TeamCatalogTeamMembership("team-1", "Alpha", true)),
                writeEnabled = false,
                dryRun = true,
            ),
        )

        mvc.perform(get("/api/team-catalog/roles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams[0].name").value("Alpha"))
            .andExpect(jsonPath("$.teams[0].isSecurityChampion").value(true))
            .andExpect(jsonPath("$.teams[0].navIdent").doesNotExist())
            .andExpect(jsonPath("$.writeEnabled").value(false))
    }

    @Test
    fun `member can submit an exact role selection`() {
        authenticated(USER_ROLE)
        whenever(service.saveSelection("person@nav.no", "A12345", setOf("team-1"))).thenReturn(
            TeamCatalogRoleSaveResult(
                teams = listOf(TeamCatalogTeamMembership("team-1", "Alpha", true)),
                updates = emptyList(),
                writeEnabled = true,
                dryRun = false,
            ),
        )

        mvc.perform(
            put("/api/team-catalog/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"selectedTeamIds":["team-1"]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.writeEnabled").value(true))

        verify(service).saveSelection("person@nav.no", "A12345", setOf("team-1"))
    }

    @Test
    fun `non-admin cannot read the role status report`() {
        authenticated(USER_ROLE)

        mvc.perform(get("/api/admin/team-catalog/roles")).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `admin can read role statuses without receiving participant details`() {
        authenticated(ADMIN_ROLE)
        whenever(service.championStatus()).thenReturn(
            TeamCatalogChampionStatusOverview(
                listOf(TeamCatalogChampionStatus("participant-1", ChampionRole.ABSENT)),
            ),
        )

        mvc.perform(get("/api/admin/team-catalog/roles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants[0].status").value("ABSENT"))
            .andExpect(jsonPath("$.participants[0].email").doesNotExist())
    }

    @Test
    fun `invalid selections return a problem response`() {
        authenticated(USER_ROLE)

        mvc.perform(
            put("/api/team-catalog/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"selectedTeamIds":["","team-1","team-1"]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid team selection"))

        verify(service, never()).saveSelection(any(), any(), any())
    }

    private fun authenticated(role: String) {
        Mockito.doAnswer { invocation ->
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("person@nav.no", "A12345"),
                null,
                listOf(SimpleGrantedAuthority("ROLE_$role")),
            )
            try {
                invocation.getArgument<FilterChain>(2).doFilter(
                    invocation.getArgument<ServletRequest>(0),
                    invocation.getArgument<ServletResponse>(1),
                )
            } finally {
                SecurityContextHolder.clearContext()
            }
            null
        }.`when`(filter).doFilter(any(), any(), any())
    }
}
