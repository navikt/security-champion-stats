package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.scoring.AddDeltaEventMappingRequest
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMapping
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMappingHasCreditsException
import navikt.appsec.securitychampionapp.app.scoring.DeltaEventMappingService
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
import java.util.UUID

@RestController
@RequestMapping("/api/admin/delta/event-mappings")
class AdminDeltaEventMappingController(
    private val service: DeltaEventMappingService,
) {
    @GetMapping
    fun mappings(): ResponseEntity<List<DeltaEventMapping>> = ResponseEntity.ok(service.mappings())

    @PostMapping
    fun addMapping(@RequestBody request: AddDeltaEventMappingRequest): ResponseEntity<Any> {
        val mapping = try {
            service.addMapping(
                request.programEventName,
                request.deltaEventUuid,
                currentPrincipal().email,
            )
        } catch (_: DuplicateKeyException) {
            throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Conflict",
                "The Delta event UUID is already mapped",
            )
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapping)
    }

    @DeleteMapping("/{id}")
    fun removeMapping(@PathVariable id: String): ResponseEntity<Any> {
        val mappingId = id.toUuid() ?: throw ApiRequestException(
            HttpStatus.BAD_REQUEST,
            "Invalid mapping ID",
            "The mapping ID is invalid",
        )
        return try {
            if (!service.removeMapping(mappingId, currentPrincipal().email)) {
                throw ApiRequestException(
                    HttpStatus.NOT_FOUND,
                    "Mapping not found",
                    "The Delta event mapping does not exist",
                )
            } else {
                ResponseEntity.noContent().build()
            }
        } catch (_: DeltaEventMappingHasCreditsException) {
            throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Mapping has awarded credits",
                "A Delta mapping with awarded credits cannot be removed",
            )
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
