package navikt.appsec.securitychampionstats.backend.routes

import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

fun Application.registerRoutes() {
    routing {
        swaggerUI(path = "openapi")
        memberRoutes()
        statsRoutes()
    }
}