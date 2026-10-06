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
        actorNavNoEmail: String,
    ): DeltaEventMapping {
        val normalizedName = programEventName.trim()
        if (normalizedName.isEmpty() || normalizedName.length > 200) {
            throw InvalidScoringRequestException("A program event name of 1 to 200 characters is required")
        }
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        val normalizedDeltaUuid = try {
            UUID.fromString(deltaEventUuid.trim())
        } catch (_: IllegalArgumentException) {
            throw InvalidScoringRequestException("A valid Delta event UUID is required")
        }
        val id = UUID.randomUUID()
        return repository.addMapping(id, normalizedName, normalizedDeltaUuid, actorNavNoEmail)
    }

    fun removeMapping(id: UUID, actorNavNoEmail: String): Boolean {
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        return repository.removeMapping(id, actorNavNoEmail)
    }
}
