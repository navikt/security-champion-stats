package navikt.appsec.securitychampionstats.server.routes

import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

fun Application.registerRoutes() {
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        memberRoutes()
        statsRoutes()
    }
}