package navikt.appsec.securitychampionapp.app.scoring

import java.time.Instant
import java.util.UUID

data class DeltaEventMapping(
    val id: UUID,
    val programEventName: String,
    val deltaEventUuid: UUID,
    val deltaCategoryId: Int?,
    val createdAt: Instant,
)

data class AddDeltaEventMappingRequest(
    val programEventName: String,
    val deltaEventUuid: String,
    val deltaCategoryId: Int,
)

data class UpdateDeltaEventMappingCategoryRequest(
    val deltaCategoryId: Int,
)

class DeltaEventMappingHasCreditsException : RuntimeException()
