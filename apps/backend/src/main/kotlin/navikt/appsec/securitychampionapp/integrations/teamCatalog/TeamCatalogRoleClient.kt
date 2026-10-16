package navikt.appsec.securitychampionapp.integrations.teamCatalog

import navikt.appsec.securitychampionapp.app.membership.InvalidTeamCatalogSelectionException
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleManagement
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleOperation
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRolePayloadPreview
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleSaveResult
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleUpdate
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleUpdateStatus
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogTeamMembership
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogDetailedMember
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogDetailedTeam
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogMemberUpdateRequest
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogMembershipResponse
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogOfficeHoursUpdateRequest
import navikt.appsec.securitychampionapp.integrations.teamCatalog.dto.TeamCatalogTeamUpdateRequest
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.util.concurrent.TimeoutException

private const val SECURITY_CHAMPION_ROLE = "SECURITY_CHAMPION"

@Service
class TeamCatalogRoleClient(
    private val externalServiceWebClient: WebClient,
    private val teamCatalogMock: TeamCatalogMock,
    private val environment: Environment,
    private val requestTimeout: Duration = Duration.ofSeconds(30),
) : TeamCatalogRoleManagement {
    private val logger = LoggerFactory.getLogger(TeamCatalogRoleClient::class.java)

    override fun memberships(email: String, navIdent: String): List<TeamCatalogTeamMembership> =
        fetchMemberships(email)
            .filter { it.status == "ACTIVE" }
            .mapNotNull { team ->
                val member = findMember(team, navIdent) ?: return@mapNotNull null
                TeamCatalogTeamMembership(
                    id = team.id,
                    name = team.name,
                    isSecurityChampion = SECURITY_CHAMPION_ROLE in member.roles.orEmpty(),
                )
            }
            .sortedBy { it.name.lowercase() }

    override fun saveSelection(
        email: String,
        navIdent: String,
        selectedTeamIds: Set<String>,
        writeEnabled: Boolean,
    ): TeamCatalogRoleSaveResult {
        val teams = fetchMemberships(email).filter { it.status == "ACTIVE" }
        val teamsWithMember = teams.mapNotNull { team ->
            findMember(team, navIdent)?.let { team to it }
        }.sortedBy { it.first.name.lowercase() }
        val memberships = teamsWithMember.map { (team, member) ->
            TeamCatalogTeamMembership(
                id = team.id,
                name = team.name,
                isSecurityChampion = SECURITY_CHAMPION_ROLE in member.roles.orEmpty(),
            )
        }
        val availableTeamIds = memberships.mapTo(mutableSetOf()) { it.id }
        if (!availableTeamIds.containsAll(selectedTeamIds)) {
            throw InvalidTeamCatalogSelectionException()
        }

        val plannedUpdates = teamsWithMember.mapNotNull { (team, member) ->
            val rolesAfter = member.roles.orEmpty().toMutableList()
            val selected = team.id in selectedTeamIds
            val currentlySelected = SECURITY_CHAMPION_ROLE in rolesAfter
            if (selected == currentlySelected) return@mapNotNull null

            val operation = if (selected) TeamCatalogRoleOperation.ADD else TeamCatalogRoleOperation.REMOVE
            if (selected) rolesAfter.add(SECURITY_CHAMPION_ROLE)
            else rolesAfter.removeAll { it == SECURITY_CHAMPION_ROLE }
            val request = team.toUpdateRequest(navIdent, rolesAfter)
            val preview = TeamCatalogRolePayloadPreview(
                id = team.id,
                name = team.name,
                teamMemberCount = team.members.orEmpty().size,
                currentMemberRoles = request.members.single { it.navIdent == navIdent }.roles,
            )
            PlannedRoleUpdate(team, operation, request, preview)
        }

        val updates = plannedUpdates.map { planned ->
            val status = if (!writeEnabled) {
                TeamCatalogRoleUpdateStatus.PREVIEW
            } else {
                try {
                    externalServiceWebClient.put()
                        .uri("/team/v2/{id}", planned.team.id)
                        .bodyValue(planned.request)
                        .retrieve()
                        .toBodilessEntity()
                        .block(requestTimeout)
                    TeamCatalogRoleUpdateStatus.UPDATED
                } catch (e: WebClientResponseException) {
                    logger.warn(
                        "Teamkatalogen role update failed for team {} with HTTP status {}",
                        planned.team.id,
                        e.statusCode.value(),
                    )
                    TeamCatalogRoleUpdateStatus.FAILED
                } catch (e: WebClientRequestException) {
                    logger.warn(
                        "Teamkatalogen role update request failed for team {} ({})",
                        planned.team.id,
                        e.javaClass.simpleName,
                    )
                    TeamCatalogRoleUpdateStatus.FAILED
                } catch (e: IllegalStateException) {
                    if (e.cause !is TimeoutException) throw e
                    logger.warn("Teamkatalogen role update timed out for team {}", planned.team.id)
                    TeamCatalogRoleUpdateStatus.FAILED
                }
            }
            TeamCatalogRoleUpdate(
                teamId = planned.team.id,
                teamName = planned.team.name,
                operation = planned.operation,
                status = status,
                payloadPreview = planned.preview,
            )
        }

        return TeamCatalogRoleSaveResult(
            teams = memberships,
            updates = updates,
            writeEnabled = writeEnabled,
            dryRun = !writeEnabled,
        )
    }

    private fun fetchMemberships(email: String): List<TeamCatalogDetailedTeam> {
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            return teamCatalogMock
                .loadMockDetailedTeamMembers(teamCatalogMock.loadMockProductAreas())
                .flatMap { it.content }
                .filter { team -> team.members.orEmpty().any { it.matches(email) } }
        }
        return requireNotNull(
            externalServiceWebClient.get()
                .uri { builder -> builder.path("/member/membership/byUserEmail").queryParam("email", email).build() }
                .retrieve()
                .bodyToMono<TeamCatalogMembershipResponse>()
                .block(requestTimeout),
        ) { "Teamkatalogen membership response was empty" }.teams
    }

    private fun findMember(team: TeamCatalogDetailedTeam, navIdent: String): TeamCatalogDetailedMember? {
        val matches = team.members.orEmpty().filter {
            it.navIdent == navIdent || it.resource?.navIdent == navIdent
        }
        check(matches.size <= 1) { "Teamkatalogen returned duplicate membership for a team member" }
        return matches.singleOrNull()
    }

    private fun TeamCatalogDetailedMember.matches(email: String): Boolean =
        resource?.email?.equals(email, ignoreCase = true) == true

    private fun TeamCatalogDetailedTeam.toUpdateRequest(
        navIdent: String,
        targetRoles: List<String>,
    ): TeamCatalogTeamUpdateRequest =
        TeamCatalogTeamUpdateRequest(
            id = id,
            name = name,
            description = description,
            slackChannel = slackChannel,
            contactPersonIdent = contactPersonIdent,
            contactAddresses = contactAddresses,
            productAreaId = productAreaId,
            teamOwnerIdent = teamOwnerIdent,
            clusterIds = clusterIds,
            teamType = teamType,
            qaTime = qaTime,
            naisTeams = naisTeams,
            members = members.orEmpty().map { member ->
                val memberNavIdent = requireNotNull(member.navIdent ?: member.resource?.navIdent) {
                    "Teamkatalogen team member did not include navIdent"
                }
                TeamCatalogMemberUpdateRequest(
                    navIdent = memberNavIdent,
                    roles = if (memberNavIdent == navIdent) {
                        targetRoles
                    } else {
                        member.roles.orEmpty()
                    },
                    description = member.description,
                    teamPercent = member.teamPercent,
                    startDate = member.startDate,
                    endDate = member.endDate,
                )
            },
            tags = tags,
            officeHours = officeHours?.let {
                TeamCatalogOfficeHoursUpdateRequest(
                    locationCode = it.location?.code,
                    days = it.days,
                    information = it.information,
                )
            },
            status = status,
        )

    private data class PlannedRoleUpdate(
        val team: TeamCatalogDetailedTeam,
        val operation: TeamCatalogRoleOperation,
        val request: TeamCatalogTeamUpdateRequest,
        val preview: TeamCatalogRolePayloadPreview,
    )
}
