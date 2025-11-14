package navikt.appsec.securitychampionstats.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

import navikt.appsec.securitychampionstats.config.Config
import navikt.appsec.securitychampionstats.connectors.PostgresRepository
import navikt.appsec.securitychampionstats.connectors.SlackService
import navikt.appsec.securitychampionstats.connectors.Teamkatalog
import navikt.appsec.securitychampionstats.models.MemberDTO
import navikt.appsec.securitychampionstats.models.Msg
import navikt.appsec.securitychampionstats.utils.Member
import navikt.appsec.securitychampionstats.utils.addMemberToJsonFile
import navikt.appsec.securitychampionstats.utils.addPointsForMemberToJsonFile
import navikt.appsec.securitychampionstats.utils.deleteMemberFromJsonFile
import navikt.appsec.securitychampionstats.utils.readMemberJsonFile
import kotlin.io.path.Path

private val logger = LoggerFactory.getLogger("MembersController")
private const val filePath = "src/main/resources/localDB.json"
private val absolutePath = Path(filePath).toAbsolutePath().toString()

// TODO: Add logic for local v1 run

fun Route.memberRoutes() {

    val teamKatalogUrl = System.getenv("TEAMKATALOG_API_URL")

    get("/api/members") {
        if (Config.DRY_RUN) {
            val jsonMembers = readMemberJsonFile(absolutePath)
            val members = jsonMembers.map { member -> MemberDTO(member.id, member.fullname, member.points, member.email) }
            call.respond(members)
        } else {
            val names: List<MemberDTO> = PostgresRepository.getAllMembers()

            if (names.isEmpty()) {
                logger.info("Database is empty or not found, try to upload members from teamkatalog")

                val teamkatalog = Teamkatalog(teamKatalogUrl)

                val membersToUpload = teamkatalog.getMembersWithRile("SECURITY_CHAMPION")
                val members: MutableList<MemberDTO> = mutableListOf()
                membersToUpload.members.members.forEach { member ->
                    members.add(MemberDTO(member.navIdent, member.fullname, 0, member.email))
                }
                PostgresRepository.addMember(members.first().fullname, members.first().id)
            }
            call.respond(names)
        }
    }

    post("/api/members"){
        val body = call.receive<MemberDTO>()
        if (body.id.isBlank() || body.fullname.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, Msg("Missing id or fullname"))
            return@post
        }
        if (Config.DRY_RUN) {
            addMemberToJsonFile(filename = absolutePath, newMember = Member(body.id, body.fullname, 0, body.email))
            call.respond(HttpStatusCode.OK, "Successfully added member to db")
        } else {
            PostgresRepository.addMember(body.fullname, body.id)
            call.respond(HttpStatusCode.Created, Msg("Added ${body.fullname} (${body.id})"))
        }
    }

    delete("/api/members/{id}") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, Msg("Missing id"))
            return@delete
        }
        if (Config.DRY_RUN) {
            deleteMemberFromJsonFile(absolutePath, id)
            call.respond(HttpStatusCode.OK, "Successfully deleted member from db")
        } else {
            // TODO: implement delete in your SQL repo:
            // PostgresRepository.deleteMember(id)
            logger.info("Delete member id={}", id)
            call.respond(Msg("Delete queued for $id (not yet implemented)"))
        }
    }
}

fun Route.statsRoutes(){

    post("/api/points") {
        val body = call.receive<Map<String, Int>>()
        val id = call.request.queryParameters["email"] ?: ""
        val points = body["points"] ?: 0
        if (Config.DRY_RUN) {
            addPointsForMemberToJsonFile(absolutePath, id, points)
            call.respond(HttpStatusCode.OK, "Successfully added points")
        } else {
            val slackService = SlackService()
            val email = call.request.queryParameters["email"] ?: ""
            val activity = slackService.summarizeActivity(email)
            val points = activity.totalMessages * 20
            PostgresRepository.updateMember(activity.userInfo.fullname, email, points)
            call.respond(Msg("Added $points points for $id (not yet implemented)"))
        }
    }
}
