package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategory
import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategorySource
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
    fun categories(): ResponseEntity<List<DeltaCategory>> =
        ResponseEntity.ok(categorySource.categories())
}
