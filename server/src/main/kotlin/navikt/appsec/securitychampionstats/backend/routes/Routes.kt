package navikt.appsec.securitychampionstats.backend.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.registerRoutes() {
    routing {
        memberRoutes()
        statsRoutes()
    }
}