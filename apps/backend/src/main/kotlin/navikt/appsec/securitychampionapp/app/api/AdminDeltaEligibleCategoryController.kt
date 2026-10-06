package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.AddDeltaEligibleCategoryRequest
import navikt.appsec.securitychampionapp.app.scoring.DeltaCategoryNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.DeltaEligibleCategory
import navikt.appsec.securitychampionapp.app.scoring.DeltaEligibleCategoryService
import navikt.appsec.securitychampionapp.app.scoring.InvalidScoringRequestException
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/delta/eligible-categories")
class AdminDeltaEligibleCategoryController(
    private val service: DeltaEligibleCategoryService,
) {
    @GetMapping
    fun categories(): ResponseEntity<List<DeltaEligibleCategory>> = ResponseEntity.ok(service.categories())

    @PostMapping
    fun addCategory(@RequestBody request: AddDeltaEligibleCategoryRequest): ResponseEntity<Any> =
        try {
            ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addCategory(request.deltaCategoryId, currentPrincipal().email))
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (_: DeltaCategoryNotFoundException) {
            ResponseEntity.badRequest().body(mapOf("error" to "The Delta category does not exist"))
        } catch (_: DuplicateKeyException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to "The Delta category is already eligible"))
        } catch (e: DeltaIntegrationException) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to e.failure.summary))
        }

    @DeleteMapping("/{categoryId}")
    fun removeCategory(@PathVariable categoryId: Int): ResponseEntity<Any> =
        try {
            if (service.removeCategory(categoryId, currentPrincipal().email)) {
                ResponseEntity.noContent().build()
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
