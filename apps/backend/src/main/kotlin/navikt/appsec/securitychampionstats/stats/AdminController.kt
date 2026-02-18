package navikt.appsec.securitychampionstats.stats

import navikt.appsec.securitychampionstats.common.hikari.PostgresRepository
import navikt.appsec.securitychampionstats.stats.dto.DeleteMember
import navikt.appsec.securitychampionstats.stats.dto.MemberInfo
import navikt.appsec.securitychampionstats.stats.dto.Points
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val repo: PostgresRepository,
) {

    @PostMapping("/member")
    fun addMember(@RequestBody memberInfo: MemberInfo): ResponseEntity<Any>{
        repo.addMember(memberInfo.fullname, memberInfo.id, memberInfo.email)
        return ResponseEntity("User was created", HttpStatus.CREATED)
    }

    @DeleteMapping("/member")
    fun deleteMember(@RequestBody member: DeleteMember): ResponseEntity<Any>{
        repo.deleteMember(member.email)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/points")
    fun addPoints(@RequestBody points: Points): ResponseEntity<Any>{
        val result = repo.addPoints(points.id, points.points)

        return if (result == 1) {
            ResponseEntity.status(HttpStatus.ACCEPTED).build()
        } else {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }



}