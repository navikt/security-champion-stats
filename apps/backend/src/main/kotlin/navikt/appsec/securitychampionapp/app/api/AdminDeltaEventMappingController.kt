package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.AddDeltaEventMappingRequest
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMapping
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMappingHasCreditsException
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMappingService
import navikt.appsec.securitychampionapp.app.scoring.InvalidScoringRequestException
import navikt.appsec.securitychampionapp.app.scoring.UpdateDeltaEventMappingCategoryRequest
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/delta/event-mappings")
class AdminDeltaEventMappingController(
    private val service: DeltaEventMappingService,
) {
    @GetMapping
    fun mappings(): ResponseEntity<List<DeltaEventMapping>> = ResponseEntity.ok(service.mappings())

    @PostMapping
    fun addMapping(@RequestBody request: AddDeltaEventMappingRequest): ResponseEntity<Any> =
        try {
            val mapping = service.addMapping(
                request.programEventName,
                request.deltaEventUuid,
                request.deltaCategoryId,
                currentPrincipal().email,
            )
            ResponseEntity.status(HttpStatus.CREATED).body(mapping)
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (_: DuplicateKeyException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to "The Delta event UUID is already mapped"))
        }

    @PutMapping("/{id}/category")
    fun updateCategory(
        @PathVariable id: String,
        @RequestBody request: UpdateDeltaEventMappingCategoryRequest,
    ): ResponseEntity<Any> {
        val mappingId = id.toUuid() ?: return ResponseEntity.badRequest().build()
        return try {
            if (service.updateCategory(mappingId, request.deltaCategoryId, currentPrincipal().email)) {
                ResponseEntity.noContent().build()
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    fun removeMapping(@PathVariable id: String): ResponseEntity<Any> {
        val mappingId = id.toUuid() ?: return ResponseEntity.badRequest().build()
        return try {
            if (!service.removeMapping(mappingId, currentPrincipal().email)) {
                ResponseEntity.notFound().build()
            } else {
                ResponseEntity.noContent().build()
            }
        } catch (e: DeltaEventMappingHasCreditsException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to "A Delta mapping with awarded credits cannot be removed"))
        } catch (e: InvalidScoringRequestException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal

    private fun String.toUuid(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
}
