package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditPage
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/audit")
class AdminAuditController(
    private val auditService: ProgramAuditService,
) {
    @GetMapping
    fun audit(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ProgramAuditPage> {
        if (
            page < 0 ||
            page > 100_000 ||
            size !in 1..100 ||
            q != null && q.length > 100 ||
            category != null && category !in setOf("all", "syncs", "credits", "admin")
        ) {
            throw ApiRequestException(
                HttpStatus.BAD_REQUEST,
                "Invalid audit query",
                "The page, size, or search query is invalid",
            )
        }
        return ResponseEntity.ok(
            auditService.adminPage(
                q?.takeIf(String::isNotBlank),
                category?.takeUnless { it == "all" },
                page,
                size,
            ),
        )
    }
}
