package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEventMappingRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeltaEventMappingService(
    private val repository: DeltaEventMappingRepository,
) {
    fun mappings(): List<DeltaEventMapping> = repository.findAll()

    fun addMapping(
        programEventName: String,
        deltaEventUuid: String,
        deltaCategoryId: Int,
        actorNavNoEmail: String,
    ): DeltaEventMapping {
        val normalizedName = programEventName.trim()
        if (normalizedName.isEmpty() || normalizedName.length > 200) {
            throw InvalidScoringRequestException("A program event name of 1 to 200 characters is required")
        }
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        if (deltaCategoryId <= 0) {
            throw InvalidScoringRequestException("A valid Delta category is required")
        }
        val normalizedDeltaUuid = try {
            UUID.fromString(deltaEventUuid.trim())
        } catch (_: IllegalArgumentException) {
            throw InvalidScoringRequestException("A valid Delta event UUID is required")
        }
        val id = UUID.randomUUID()
        return repository.addMapping(id, normalizedName, normalizedDeltaUuid, deltaCategoryId, actorNavNoEmail)
    }

    fun updateCategory(id: UUID, deltaCategoryId: Int, actorNavNoEmail: String): Boolean {
        if (deltaCategoryId <= 0) {
            throw InvalidScoringRequestException("A valid Delta category is required")
        }
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        return repository.updateCategory(id, deltaCategoryId, actorNavNoEmail)
    }

    fun removeMapping(id: UUID, actorNavNoEmail: String): Boolean {
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        return repository.removeMapping(id, actorNavNoEmail)
    }
}
