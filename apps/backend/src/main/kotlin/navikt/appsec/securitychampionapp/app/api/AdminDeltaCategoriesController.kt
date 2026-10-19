package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategory
import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategorySource
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/delta/categories")
@Tag(name = "Delta integration")
class AdminDeltaCategoriesController(
    private val categorySource: DeltaCategorySource,
) {
    @GetMapping
    @Operation(summary = "List Delta categories")
    fun categories(): ResponseEntity<List<DeltaCategory>> =
        ResponseEntity.ok(categorySource.categories())
}
