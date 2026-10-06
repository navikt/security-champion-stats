package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.api.dto.AddMember
import navikt.appsec.securitychampionapp.app.api.dto.AdminProgramParticipantView
import navikt.appsec.securitychampionapp.app.api.dto.DeleteParticipantRequest
import navikt.appsec.securitychampionapp.app.api.dto.Event
import navikt.appsec.securitychampionapp.app.api.dto.SCdata
import navikt.appsec.securitychampionapp.app.api.dto.UpdateParticipantStatusRequest
import navikt.appsec.securitychampionapp.integrations.postgress.EventRepository
import navikt.appsec.securitychampionapp.integrations.postgress.MemberRepository
import navikt.appsec.securitychampionapp.integrations.postgress.ProgramParticipantRepository
import navikt.appsec.securitychampionapp.integrations.postgress.dto.EventType
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import navikt.appsec.securitychampionapp.utils.Validate
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
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
    private val participantRepository: ProgramParticipantRepository,
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
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
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
    fun getProgramParticipants(): ResponseEntity<List<AdminProgramParticipantView>> {
        val response = participantRepository.findAllParticipants()
        if (!response.isOk) {
            logger.error("Failed to fetch program participants: ${response.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        return ResponseEntity.ok(
            response.queryResult.map {
                AdminProgramParticipantView(
                    id = it.id,
                    email = it.email,
                    fullname = it.fullname,
                    teams = it.teams,
                    active = it.status == "ACTIVE",
                    joinedAt = it.createdAt,
                    status = it.status,
                )
            }
        )
    }

    @PutMapping("/participants/{id}/status", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun updateParticipantStatus(
        @PathVariable id: String,
        @RequestBody request: UpdateParticipantStatusRequest,
    ): ResponseEntity<Any> {
        val participantId = id.toUuid() ?: return ResponseEntity.badRequest().build()
        val response = participantRepository.updateStatus(
            participantId,
            request.active,
            currentPrincipal().email,
        )
        if (!response.isOk) {
            logger.error("Failed to update participant status: ${response.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        if (response.affectedRows == 0) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/participants/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun permanentlyDeleteParticipant(
        @PathVariable id: String,
        @RequestBody request: DeleteParticipantRequest,
    ): ResponseEntity<Any> {
        if (!request.confirmed || request.reason.isBlank()) {
            return ResponseEntity.badRequest().body("Confirmation and a reason are required")
        }
        val participantId = id.toUuid() ?: return ResponseEntity.badRequest().build()
        val response = participantRepository.permanentlyDelete(
            participantId,
        )
        if (!response.isOk) {
            logger.error("Failed to permanently delete participant: ${response.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
        if (response.affectedRows == 0) return ResponseEntity.notFound().build()
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
            return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, validationError)
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
        val result = try {
            eventRepository.addEvent(created)
        } catch (_: DuplicateKeyException) {
            logger.warn("Rejected duplicate event creation")
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "An event with this name, start time and location already exists",
                )
            )
        }
        if (!result.isOk) {
            logger.warn("Failed to add event due to error: ${result.error}")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add event")
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
