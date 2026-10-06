package navikt.appsec.securitychampionapp.app.scoring

import navikt.appsec.securitychampionapp.integrations.delta.DeltaCategorySource
import navikt.appsec.securitychampionapp.integrations.postgress.DeltaEligibleCategoryRepository
import org.springframework.stereotype.Service

@Service
class DeltaEligibleCategoryService(
    private val repository: DeltaEligibleCategoryRepository,
    private val categorySource: DeltaCategorySource,
) {
    fun categories(): List<DeltaEligibleCategory> = repository.findAll()

    fun addCategory(deltaCategoryId: Int, actorNavNoEmail: String): DeltaEligibleCategory {
        if (deltaCategoryId <= 0) throw InvalidScoringRequestException("A valid Delta category is required")
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        val category = categorySource.categories().firstOrNull { it.id == deltaCategoryId }
            ?: throw DeltaCategoryNotFoundException()
        return repository.add(category.id, category.name.trim(), actorNavNoEmail)
    }

    fun removeCategory(deltaCategoryId: Int, actorNavNoEmail: String): Boolean {
        if (actorNavNoEmail.isBlank()) {
            throw InvalidScoringRequestException("An administrator identity is required")
        }
        return repository.remove(deltaCategoryId, actorNavNoEmail)
    }
}
