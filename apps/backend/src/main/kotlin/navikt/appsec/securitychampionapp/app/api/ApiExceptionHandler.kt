package navikt.appsec.securitychampionapp.app.api

import navikt.appsec.securitychampionapp.app.events.EventClaimException
import navikt.appsec.securitychampionapp.app.events.EventClaimFailure
import navikt.appsec.securitychampionapp.app.events.EventReminderConflictException
import navikt.appsec.securitychampionapp.app.events.EventSignupUnavailableException
import navikt.appsec.securitychampionapp.app.events.InvalidEventReminderMessageException
import navikt.appsec.securitychampionapp.app.scoring.InvalidScoringRequestException
import navikt.appsec.securitychampionapp.app.scoring.ScoringTargetNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.SourceCreditNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.StaleScoringConfigurationException
import navikt.appsec.securitychampionapp.integrations.delta.DeltaIntegrationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

@RestControllerAdvice
class ApiExceptionHandler {
    private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(InvalidEventReminderMessageException::class)
    fun invalidReminderMessage(exception: InvalidEventReminderMessageException, request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.BAD_REQUEST, "Invalid reminder message", requireNotNull(exception.message), request)

    @ExceptionHandler(EventReminderConflictException::class)
    fun reminderConflict(exception: EventReminderConflictException, request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.CONFLICT, "Reminder preview changed", requireNotNull(exception.message), request)

    @ExceptionHandler(EventSignupUnavailableException::class)
    fun signupUnavailable(request: WebRequest): ResponseEntity<ProblemDetail> {
        logger.warn("Event reminder request blocked because Delta signup information is unavailable")
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Signup information unavailable", "Delta signup information could not be verified", request)
    }

    @ExceptionHandler(ApiRequestException::class)
    fun apiRequestFailure(exception: ApiRequestException, request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(exception.status, exception.title, exception.message, request)

    @ExceptionHandler(EventClaimException::class)
    fun eventClaimFailure(exception: EventClaimException, request: WebRequest): ResponseEntity<ProblemDetail> {
        val (status, title) = when (exception.failure) {
            EventClaimFailure.INVALID -> HttpStatus.BAD_REQUEST to "Invalid claim"
            EventClaimFailure.FORBIDDEN -> HttpStatus.FORBIDDEN to "Forbidden"
            EventClaimFailure.CONFLICT -> HttpStatus.CONFLICT to "Claim conflict"
            EventClaimFailure.NOT_FOUND -> HttpStatus.NOT_FOUND to "Claim not found"
        }
        return problem(status, title, requireNotNull(exception.message), request)
    }

    @ExceptionHandler(InvalidScoringRequestException::class)
    fun invalidScoringRequest(exception: InvalidScoringRequestException, request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.message ?: "The request is invalid", request)

    @ExceptionHandler(StaleScoringConfigurationException::class)
    fun staleScoringConfiguration(exception: StaleScoringConfigurationException, request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.CONFLICT, "Scoring changed", requireNotNull(exception.message), request)

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun badRequest(request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request is invalid", request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", "The HTTP method is not supported", request)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun unsupportedMediaType(request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", "The request content type is not supported", request)

    @ExceptionHandler(ScoringTargetNotFoundException::class)
    fun participantNotFound(
        exception: ScoringTargetNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.NOT_FOUND, "Participant not found", exception.message ?: "The participant does not exist", request)

    @ExceptionHandler(NoResourceFoundException::class)
    fun routeNotFound(request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.NOT_FOUND, "Not found", "The requested resource does not exist", request)

    @ExceptionHandler(SourceCreditNotFoundException::class)
    fun sourceCreditNotFound(
        exception: SourceCreditNotFoundException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.BAD_REQUEST, "Invalid source credit", exception.message ?: "The source credit is invalid", request)

    @ExceptionHandler(DuplicateKeyException::class)
    fun duplicate(request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.CONFLICT, "Conflict", "The requested resource already exists", request)

    @ExceptionHandler(DeltaIntegrationException::class)
    fun deltaUnavailable(
        exception: DeltaIntegrationException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.SERVICE_UNAVAILABLE, "Integration unavailable", exception.failure.summary, request)

    @ExceptionHandler(DataAccessException::class)
    fun databaseFailure(exception: DataAccessException, request: WebRequest): ResponseEntity<ProblemDetail> {
        logger.error("Database request failed", exception)
        return internalError(request)
    }

    @ExceptionHandler(Exception::class)
    fun unexpectedFailure(exception: Exception, request: WebRequest): ResponseEntity<ProblemDetail> {
        logger.error("API request failed", exception)
        return internalError(request)
    }

    private fun internalError(request: WebRequest): ResponseEntity<ProblemDetail> =
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "The request could not be completed",
            request,
        )

    private fun problem(
        status: HttpStatusCode,
        title: String,
        detail: String,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val response = ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            instance = (request as? ServletWebRequest)?.request?.requestURI?.let(URI::create)
        }
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(response)
    }
}
