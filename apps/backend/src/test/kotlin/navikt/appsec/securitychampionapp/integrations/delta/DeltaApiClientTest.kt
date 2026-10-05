package navikt.appsec.securitychampionapp.integrations.delta

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class DeltaApiClientTest {
    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<CapturedRequest>()

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requests += CapturedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = body,
            )
            val (status, response) = when (exchange.requestURI.path) {
                "/token" -> 200 to """{"access_token":"delta-test-token","expires_in":3600,"token_type":"Bearer"}"""
                "/event/$FORBIDDEN_EVENT" -> 403 to """{"email":"private@nav.no","detail":"raw response"}"""
                else -> 200 to """
                    {
                      "event": {
                        "id": "$EVENT_ID",
                        "startTime": "2026-10-03T10:00:00",
                        "title": "Synthetic event"
                      },
                      "participants": [
                        {"email": "participant@nav.no", "name": "Synthetic participant"}
                      ],
                      "hosts": [
                        {"email": "host@nav.no", "name": "Synthetic host"}
                      ],
                      "categories": []
                    }
                """.trimIndent()
            }
            val responseBytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `should fetch only participant registrations using a Nais M2M token`() {
        val baseUrl = "http://localhost:${server.address.port}"
        val client = client(baseUrl)

        val result = client.fetchEvent(EVENT_ID)

        assertThat(result.eventUuid).isEqualTo(EVENT_ID)
        assertThat(result.startTime).isEqualTo("2026-10-03T10:00:00")
        assertThat(result.participantEmails).containsExactly("participant@nav.no")
        assertThat(requests.map { it.path }).containsExactly(
            "/token",
            "/event/$EVENT_ID",
        )
        assertThat(requests[0].method).isEqualTo("POST")
        assertThat(decodeForm(requests[0].body)).isEqualTo(
            mapOf(
                "identity_provider" to "entra_id",
                "target" to "prod-gcp:delta:delta-backend",
            )
        )
        assertThat(requests[1].authorization).isEqualTo("Bearer delta-test-token")
    }

    @Test
    fun `should not expose Delta error response bodies`() {
        val baseUrl = "http://localhost:${server.address.port}"

        assertThatThrownBy { client(baseUrl).fetchEvent(FORBIDDEN_EVENT) }
            .isInstanceOf(DeltaIntegrationException::class.java)
            .hasMessage(DeltaFailure.API.summary)
            .hasMessageNotContaining("private@nav.no")
            .hasMessageNotContaining("raw response")
    }

    @Test
    fun `should sanitize token endpoint transport failures`() {
        val baseUrl = "http://localhost:${server.address.port}"
        server.stop(0)

        assertThatThrownBy { client(baseUrl).fetchEvent(EVENT_ID) }
            .isInstanceOf(DeltaIntegrationException::class.java)
            .hasMessage(DeltaFailure.TOKEN.summary)
            .hasMessageNotContaining("localhost")
    }

    private fun client(baseUrl: String) = DeltaApiClient(
        apiClient = WebClient.builder().baseUrl(baseUrl).build(),
        tokenClient = WebClient.builder().build(),
        tokenEndpoint = "$baseUrl/token",
        target = "prod-gcp:delta:delta-backend",
    )

    private fun decodeForm(body: String): Map<String, String> =
        body.split("&")
            .associate { pair ->
                val (key, value) = pair.split("=", limit = 2)
                URLDecoder.decode(key, StandardCharsets.UTF_8) to
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String?,
        val body: String,
    )

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val FORBIDDEN_EVENT: UUID = UUID.fromString("423e4567-e89b-12d3-a456-426614174000")
    }
}
