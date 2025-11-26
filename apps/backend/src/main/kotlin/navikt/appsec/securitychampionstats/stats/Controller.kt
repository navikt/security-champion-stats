package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.integration.postgres.PostgresRepository
import navikt.appsec.securitychampionstats.integration.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.stats.dto.DeleteMember
import navikt.appsec.securitychampionstats.stats.dto.Member
import navikt.appsec.securitychampionstats.stats.dto.MemberInfo
import navikt.appsec.securitychampionstats.stats.dto.Points
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping(path = ["/api"])
class Controller(
    private val repo: PostgresRepository,
    private val catalog: TeamCatalog
) {
    private val logger = LoggerFactory.getLogger(Controller::class.java)

    @GetMapping("/health")
    fun healthCheck(): String = "OK"

    @GetMapping("/members")
    fun getAllMembers(): List<Member> {
        val members = repo.getAllMembers()
        return members.ifEmpty {
            logger.info("fetching all members from team catalog")
            val members = catalog.fetchMembersWithRole("SECURITY_CHAMPIONS")
            repo.addMembers(members)
            repo.getAllMembers()
        }

    }

    @PostMapping("/member")
    fun addMember(@RequestBody memberInfo: MemberInfo): ResponseEntity<Any>{
        val id = UUID.randomUUID().toString()
        repo.addMember(memberInfo.fullname, id)
        return ResponseEntity("User was created", HttpStatus.CREATED)
    }

    @DeleteMapping("/member")
    fun deleteMember(@RequestBody member: DeleteMember): ResponseEntity<Any>{
        repo.deleteMember(member.id)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/points")
    fun addPoints(@RequestBody points: Points): ResponseEntity<Any>{
        repo.addPoints(points.id, points.points)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}