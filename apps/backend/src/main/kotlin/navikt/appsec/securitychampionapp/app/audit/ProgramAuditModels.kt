package navikt.appsec.securitychampionapp.app.audit

import java.time.Instant
import java.util.UUID

enum class AuditOutcome {
    SUCCEEDED,
    FAILED,
    PARTIAL,
}

data class AuditRunContext(
    val correlationId: UUID = UUID.randomUUID(),
    val actorNavNoEmail: String? = null,
    val actorParticipantId: UUID? = null,
)

data class ProgramAuditEntry(
    val id: UUID,
    val createdAt: Instant,
    val action: String,
    val outcome: AuditOutcome,
    val actorNavNoEmail: String?,
    val targetParticipantId: UUID?,
    val correlationId: UUID?,
    val details: Map<String, String>,
)

data class ProgramAuditPage(
    val items: List<ProgramAuditEntry>,
    val total: Long,
    val page: Int,
    val size: Int,
)

data class ParticipantHistoryEntry(
    val id: String,
    val occurredAt: Instant,
    val type: String,
    val action: String,
    val status: String?,
    val creditType: String?,
    val points: Int?,
    val sourceReference: String?,
    val reason: String?,
)
