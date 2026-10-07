package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.api.dto.AddMember
import navikt.appsec.securitychampionapp.app.api.dto.AdminProgramParticipantView
import navikt.appsec.securitychampionapp.app.api.dto.DeleteParticipantRequest
import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.api.dto.SCdata
import navikt.appsec.securitychampionapp.app.api.dto.UpdateParticipantStatusRequest
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.app.participation.ParticipationStatus
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.MemberRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.EventType
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import navikt.appsec.securitychampionapp.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import java.time.Instant
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val repo: MemberRepository,
    private val participantRepository: ParticipantStore,
    private val validate: Validate,
    private val eventRepository: EventRepository,
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)

    @PostMapping("/member", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addMember(@RequestBody memberInfo: AddMember): ResponseEntity<Any> {
        if (!validate.isValidEmail(memberInfo.email) or !validate.isValidName(memberInfo.fullName)) {
            logger.warn(
                "Attempt to add member failed due to invalid email format, " +
                    "request made by user ${SecurityContextHolder.getContext().authentication?.name}"
            )
            throw ApiRequestException(
                HttpStatus.BAD_REQUEST,
                "Invalid member",
                "The email or full name is invalid",
            )
        }
        val id = UUID.randomUUID().toString()
        repo.addMember(memberInfo.fullName, id = id, memberInfo.email, emptyList())
        return ResponseEntity("User was created", HttpStatus.CREATED)
    }

    @DeleteMapping("/member/{id}")
    fun deleteMember(@PathVariable id: String): ResponseEntity<Any> {
        repo.deleteMember(id)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @GetMapping("/participants")
    fun getProgramParticipants(): ResponseEntity<List<AdminProgramParticipantView>> =
        ResponseEntity.ok(
            participantRepository.findAllParticipants().map {
                AdminProgramParticipantView(
                    id = it.id.toString(),
                    email = it.email,
                    fullname = it.fullname,
                    teams = it.teams,
                    active = it.status == ParticipationStatus.ACTIVE,
                    joinedAt = it.createdAt,
                    status = it.status.name,
                )
            }
        )

    @PutMapping("/participants/{id}/status", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateParticipantStatus(
        @PathVariable id: String,
        @RequestBody request: UpdateParticipantStatusRequest,
    ): ResponseEntity<Any> {
        val participantId = id.toUuid() ?: throw ApiRequestException(
            HttpStatus.BAD_REQUEST,
            "Invalid participant ID",
            "The participant ID is invalid",
        )
        val affectedRows = participantRepository.updateStatus(
            participantId,
            request.active,
            currentPrincipal().email,
        )
        if (affectedRows == 0) throw ApiRequestException(HttpStatus.NOT_FOUND, "Participant not found", "The participant does not exist")
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/participants/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun permanentlyDeleteParticipant(
        @PathVariable id: String,
        @RequestBody request: DeleteParticipantRequest,
    ): ResponseEntity<Any> {
        if (!request.confirmed || request.reason.isBlank()) {
            throw ApiRequestException(
                HttpStatus.BAD_REQUEST,
                "Invalid deletion request",
                "Confirmation and a reason are required",
            )
        }
        val participantId = id.toUuid() ?: throw ApiRequestException(
            HttpStatus.BAD_REQUEST,
            "Invalid participant ID",
            "The participant ID is invalid",
        )
        val affectedRows = participantRepository.permanentlyDelete(
            participantId,
        )
        if (affectedRows == 0) throw ApiRequestException(HttpStatus.NOT_FOUND, "Participant not found", "The participant does not exist")
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/events")
    fun addEvent(@RequestBody event: Event): ResponseEntity<Any> {
        val startDate = runCatching { Instant.parse(event.startDate) }.getOrNull()
        val endDate = runCatching { Instant.parse(event.endDate) }.getOrNull()
        val validationError = when {
            event.id.toUuid() == null -> "Invalid event ID"
            event.name.trim().isEmpty() || event.name.trim().length > 100 ->
                "Event name must contain between 1 and 100 characters"
            event.location.trim().length > 100 -> "Event location must not exceed 100 characters"
            startDate == null || endDate == null -> "Invalid date format"
            endDate <= startDate -> "End must be after start"
            event.type.uppercase(Locale.ROOT) !in EventType.entries.map { it.name } -> "Invalid event type"
            else -> null
        }
        if (validationError != null) {
            logger.warn("Rejected event creation: {}", validationError)
            throw ApiRequestException(
                HttpStatus.BAD_REQUEST,
                "Invalid event",
                validationError,
            )
        }
        val created = event.copy(
            name = event.name.trim(),
            location = event.location.trim(),
            type = event.type.lowercase(Locale.ROOT),
            amountOfPeopleJoined = 0,
            link = null,
        )

        logger.info("Adding event: ${event.id}")
        try {
            eventRepository.addEvent(created)
        } catch (_: DuplicateKeyException) {
            logger.warn("Rejected duplicate event creation")
            throw ApiRequestException(
                HttpStatus.CONFLICT,
                "Conflict",
                "An event with this name, start time and location already exists",
            )
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/dashboard/members")
    fun getAllMembers(): ResponseEntity<List<SCdata>> =
        ResponseEntity.ok(repo.getSCAmountOverTime())

    @PostMapping("/member/attended/{email}")
    fun validateMemberAttendingMeeting(@PathVariable email: String): ResponseEntity<Any> =
        ResponseEntity.ok().build()

    private fun String.toUuid(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private fun currentPrincipal(): AppPrincipal =
        requireNotNull(SecurityContextHolder.getContext().authentication).principal as AppPrincipal
}
