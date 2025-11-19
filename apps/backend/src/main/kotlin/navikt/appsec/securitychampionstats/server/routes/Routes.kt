package navikt.appsec.securitychampionstats.server.routes

import io.ktor.server.application.Application
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.routing

fun Application.registerRoutes() {
    routing {
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
        memberRoutes()
        statsRoutes()
    }
}