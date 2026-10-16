package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.membership.InvalidTeamCatalogSelectionException
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleAccessException
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleSaveResult
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleService
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleState
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogChampionStatusOverview
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SaveTeamCatalogRolesRequest(val selectedTeamIds: List<String>)

@RestController
@RequestMapping("/api")
class TeamCatalogRoleController(
    private val teamCatalogRoleService: TeamCatalogRoleService,
) {
    @GetMapping("/team-catalog/roles")
    fun memberships(): TeamCatalogRoleState =
        withPrincipal { principal ->
            teamCatalogRoleService.currentTeams(principal.email, principal.navIdent)
        }

    @PutMapping("/team-catalog/roles")
    fun saveSelection(@RequestBody request: SaveTeamCatalogRolesRequest): TeamCatalogRoleSaveResult {
        if (request.selectedTeamIds.any { it.isBlank() } ||
            request.selectedTeamIds.size != request.selectedTeamIds.toSet().size
        ) {
            throw ApiRequestException(
                HttpStatus.BAD_REQUEST,
                "Invalid team selection",
                "Selected team IDs must be non-empty and unique",
            )
        }
        return withPrincipal { principal ->
            teamCatalogRoleService.saveSelection(
                principal.email,
                principal.navIdent,
                request.selectedTeamIds.toSet(),
            )
        }
    }

    @GetMapping("/admin/team-catalog/roles")
    fun championStatus(): TeamCatalogChampionStatusOverview =
        teamCatalogRoleService.championStatus()

    private fun <T> withPrincipal(action: (AppPrincipal) -> T): T =
        try {
            action(
                requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal,
            )
        } catch (_: TeamCatalogRoleAccessException) {
            throw ApiRequestException(
                HttpStatus.FORBIDDEN,
                "Active membership required",
                "An active program membership is required to manage Teamkatalogen roles",
            )
        } catch (_: InvalidTeamCatalogSelectionException) {
            throw ApiRequestException(
                HttpStatus.BAD_REQUEST,
                "Invalid team selection",
                "Select only teams in your current Teamkatalogen membership",
            )
        }
}
