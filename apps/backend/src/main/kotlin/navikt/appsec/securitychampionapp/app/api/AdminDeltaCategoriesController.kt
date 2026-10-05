package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategory
import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategorySource
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/delta/categories")
class AdminDeltaCategoriesController(
    private val categorySource: DeltaCategorySource,
) {
    @GetMapping
    fun categories(): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(categorySource.categories())
        } catch (e: DeltaIntegrationException) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("error" to e.failure.summary))
        }
}
