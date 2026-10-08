package navikt.appsec.securitychampionapp.app.participation

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

enum class ParticipationStatus {
    ACTIVE,
    LEFT,
    DEACTIVATED,
}

data class ProgramParticipant(
    val id: UUID,
    val navNoEmail: String,
    val navIdent: String?,
    val email: String,
    val fullname: String,
    val teams: List<String>,
    val status: ParticipationStatus,
    val createdAt: String,
)

data class ParticipantProfile(
    val fullname: String,
    val teams: List<String>,
)

sealed interface ParticipantProfileLookup {
    data class Available(val profile: ParticipantProfile?) : ParticipantProfileLookup

    data object Unavailable : ParticipantProfileLookup
}

interface ParticipantStore {
    fun findById(id: UUID): ProgramParticipant?

    fun findByNavNoEmail(navNoEmail: String): ProgramParticipant?

    fun findActiveParticipants(): List<ProgramParticipant>

    fun findAllParticipants(): List<ProgramParticipant>

    fun enroll(
        id: UUID,
        navNoEmail: String,
        navIdent: String,
        email: String,
        fullname: String,
        teams: List<String>,
    ): Int

    fun updateAuthenticatedIdentity(navNoEmail: String, navIdent: String, email: String): Int

    fun leave(navNoEmail: String): Int

    fun rejoin(navNoEmail: String): Int

    fun updateProfile(navIdent: String, email: String, fullname: String, teams: List<String>): Int

    fun updateStatus(id: UUID, active: Boolean, actorNavNoEmail: String): Int

    fun permanentlyDelete(id: UUID): Int
}

fun interface ParticipantProfileSource {
    fun lookup(navIdent: String, email: String): ParticipantProfileLookup
}

enum class EnrollmentOutcome {
    ENROLLED,
    REJOINED,
    ALREADY_ENROLLED,
    DEACTIVATED,
    CONFLICT,
}

enum class LeaveOutcome {
    LEFT,
    ALREADY_LEFT,
    NOT_FOUND,
    DEACTIVATED,
    CONFLICT,
}

data class ParticipantEnrolledEvent(val participantId: UUID)

@Service
class ParticipantLifecycle(
    private val participants: ParticipantStore,
    private val profiles: ParticipantProfileSource,
    private val auditService: ProgramAuditService,
    private val events: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(ParticipantLifecycle::class.java)

    fun enroll(navNoEmail: String, navIdent: String, email: String): EnrollmentOutcome {
        val existing = participants.findByNavNoEmail(navNoEmail)
        if (existing != null) return enrollExisting(existing)

        val lookup = profiles.lookup(navIdent, email)
        val profile = when (lookup) {
            is ParticipantProfileLookup.Available -> lookup.profile
            ParticipantProfileLookup.Unavailable -> {
                logger.warn("Teamkatalogen profile lookup was unavailable during participant enrollment")
                null
            }
        }
        val created = participants.enroll(
            UUID.randomUUID(),
            navNoEmail,
            navIdent,
            email,
            profile?.fullname.orEmpty(),
            profile?.teams.orEmpty(),
        )
        if (created == 0) {
            return participants.findByNavNoEmail(navNoEmail)?.let(::enrollExisting)
                ?: EnrollmentOutcome.CONFLICT
        }

        val participant = requireNotNull(participants.findByNavNoEmail(navNoEmail)) {
            "Newly enrolled participant could not be read"
        }
        auditService.recordParticipantEvent(
            participant.id,
            "PARTICIPANT_ENROLLED",
            navNoEmail,
            details = mapOf("status" to ParticipationStatus.ACTIVE.name),
        )
        events.publishEvent(ParticipantEnrolledEvent(participant.id))
        return EnrollmentOutcome.ENROLLED
    }

    fun leave(navNoEmail: String): LeaveOutcome {
        val participant = participants.findByNavNoEmail(navNoEmail) ?: return LeaveOutcome.NOT_FOUND
        when (participant.status) {
            ParticipationStatus.LEFT -> return LeaveOutcome.ALREADY_LEFT
            ParticipationStatus.DEACTIVATED -> return LeaveOutcome.DEACTIVATED
            ParticipationStatus.ACTIVE -> Unit
        }
        if (participants.leave(navNoEmail) == 0) return LeaveOutcome.CONFLICT

        auditService.recordParticipantEvent(
            participant.id,
            "PARTICIPANT_LEFT",
            navNoEmail,
            details = mapOf("status" to ParticipationStatus.LEFT.name),
        )
        return LeaveOutcome.LEFT
    }

    private fun enrollExisting(participant: ProgramParticipant): EnrollmentOutcome =
        when (participant.status) {
            ParticipationStatus.ACTIVE -> EnrollmentOutcome.ALREADY_ENROLLED
            ParticipationStatus.DEACTIVATED -> EnrollmentOutcome.DEACTIVATED
            ParticipationStatus.LEFT -> {
                if (participants.rejoin(participant.navNoEmail) == 0) {
                    EnrollmentOutcome.CONFLICT
                } else {
                    auditService.recordParticipantEvent(
                        participant.id,
                        "PARTICIPANT_REJOINED",
                        participant.navNoEmail,
                        details = mapOf("status" to ParticipationStatus.ACTIVE.name),
                    )
                    events.publishEvent(ParticipantEnrolledEvent(participant.id))
                    EnrollmentOutcome.REJOINED
                }
            }
        }
}
