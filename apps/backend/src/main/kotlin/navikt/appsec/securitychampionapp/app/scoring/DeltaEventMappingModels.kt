package navikt.appsec.securitychampionapp.app.scoring

import java.time.Instant
import java.util.UUID

data class DeltaEventMapping(
    val id: UUID,
    val programEventName: String,
    val deltaEventUuid: UUID,
    val createdAt: Instant,
)

data class AddDeltaEventMappingRequest(
    val programEventName: String,
    val deltaEventUuid: String,
)

data class DeltaEligibleCategory(
    val categoryId: Int,
    val categoryName: String,
    val createdAt: Instant,
)

data class AddDeltaEligibleCategoryRequest(
    val deltaCategoryId: Int,
)

class DeltaEventMappingHasCreditsException : RuntimeException()

class DeltaCategoryNotFoundException : RuntimeException()
