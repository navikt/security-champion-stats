package navikt.appsec.securitychampionapp.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import tools.jackson.databind.ObjectMapper
import java.net.URI

fun writeProblemDetail(
    response: HttpServletResponse,
    request: HttpServletRequest,
    status: HttpStatus,
    title: String,
    detail: String,
    objectMapper: ObjectMapper,
) {
    response.status = status.value()
    response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    val problem = ProblemDetail.forStatusAndDetail(status, detail).apply {
        this.title = title
        instance = URI.create(request.requestURI)
    }
    objectMapper.writeValue(response.outputStream, problem)
}
