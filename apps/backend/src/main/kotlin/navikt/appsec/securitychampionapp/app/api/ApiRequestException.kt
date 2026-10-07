package navikt.appsec.securitychampionapp.app.api

import org.springframework.http.HttpStatus

class ApiRequestException(
    val status: HttpStatus,
    val title: String,
    override val message: String,
) : RuntimeException(message)
