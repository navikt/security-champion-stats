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
import java.time.LocalDateTime
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
                query = exchange.requestURI.rawQuery.orEmpty(),
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = body,
            )
            val (status, response) = when (exchange.requestURI.path) {
                "/token" -> 200 to """{"access_token":"delta-test-token","expires_in":3600,"token_type":"Bearer"}"""
                "/category" -> 200 to """[{"id":7,"name":"Security"}]"""
                "/event" -> if (
                    exchange.requestURI.rawQuery.orEmpty().contains("participantEmail=private")
                ) {
                    403 to """{"email":"private@nav.no","detail":"raw response"}"""
                } else {
                    200 to """
                        [{
                          "event": {
                            "id": "$EVENT_ID",
                            "startTime": "2026-10-03T10:00:00",
                            "title": "Synthetic event"
                          }
                        }]
                    """.trimIndent()
                }
                else -> 404 to """{}"""
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
    fun `should fetch participant-specific event matches using a Nais M2M token`() {
        val baseUrl = "http://localhost:${server.address.port}"
        val client = client(baseUrl)

        val result = client.findRegisteredEvents(
            categoryId = 7,
            participantEmail = "participant@nav.no",
            from = LocalDateTime.parse("2026-01-01T00:00:00"),
            to = LocalDateTime.parse("2027-01-01T00:00:00"),
        )

        assertThat(result).containsExactly(
            DeltaEventMatch(EVENT_ID, LocalDateTime.parse("2026-10-03T10:00:00"))
        )
        assertThat(requests.map { it.path }).containsExactly("/token", "/event")
        assertThat(decodeQuery(requests[1].query)).isEqualTo(
            mapOf(
                "categories" to "7",
                "participantEmail" to "participant@nav.no",
                "from" to "2026-01-01T00:00:00",
                "to" to "2027-01-01T00:00:00",
            )
        )
        assertThat(requests[0].method).isEqualTo("POST")
        assertThat(decodeForm(requests[0].body)).isEqualTo(
            mapOf(
                "identity_provider" to "entra_id",
                "target" to "prod-gcp:delta:delta-backend",
            )
        )
        assertThat(requests[1].authorization).startsWith("Bearer ")
    }

    @Test
    fun `should fetch category types`() {
        val categories = client("http://localhost:${server.address.port}").categories()

        assertThat(categories).containsExactly(DeltaCategory(7, "Security"))
        assertThat(requests.map { it.path }).containsExactly("/token", "/category")
    }

    @Test
    fun `should not expose Delta error response bodies`() {
        val baseUrl = "http://localhost:${server.address.port}"

        assertThatThrownBy {
            client(baseUrl).findRegisteredEvents(
                7,
                "private@nav.no",
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2027-01-01T00:00:00"),
            )
        }
            .isInstanceOf(DeltaIntegrationException::class.java)
            .hasMessage(DeltaFailure.API.summary)
            .hasMessageNotContaining("private@nav.no")
            .hasMessageNotContaining("raw response")
    }

    @Test
    fun `should sanitize token endpoint transport failures`() {
        val baseUrl = "http://localhost:${server.address.port}"
        server.stop(0)

        assertThatThrownBy { client(baseUrl).categories() }
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

    private fun decodeQuery(query: String): Map<String, String> =
        query.split("&")
            .associate { pair ->
                val (key, value) = pair.split("=", limit = 2)
                URLDecoder.decode(key, StandardCharsets.UTF_8) to
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val query: String,
        val authorization: String?,
        val body: String,
    )

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    }
}
