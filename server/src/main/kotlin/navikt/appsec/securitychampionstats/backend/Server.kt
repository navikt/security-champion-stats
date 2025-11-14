package navikt.appsec.securitychampionstats.backend

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.cors.routing.*
import org.slf4j.LoggerFactory

import navikt.appsec.securitychampionstats.backend.routes.registerRoutes

object Server {
    private val logger = LoggerFactory.getLogger(Server::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val port = System.getenv("PORT")?.toInt() ?: 8080
        embeddedServer(Netty, port = port) {
            install(CallLogging)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(CORS) {
                allowMethod(io.ktor.http.HttpMethod.Get)
                allowMethod(io.ktor.http.HttpMethod.Post)
                allowMethod(io.ktor.http.HttpMethod.Delete)
                allowHeader(io.ktor.http.HttpHeaders.ContentType)
                anyHost()
            }
            registerRoutes()
        }.start(wait = true)
        logger.info("Backend is running on the port: $port")
    }
}