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
    private var eventJson = FULL_EVENT_JSON

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
                "/event" -> if (exchange.requestURI.rawQuery.orEmpty().contains("categories=13")) {
                    403 to """{"email":"private@nav.no","detail":"raw response"}"""
                } else {
                    200 to "[$eventJson]"
                }
                "/event/$EVENT_ID" -> 200 to eventJson
                "/event/$MISSING_EVENT_ID" -> 404 to """{}"""
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
    fun `should fetch past events in a category using a Nais M2M token`() {
        val baseUrl = "http://localhost:${server.address.port}"

        val result = client(baseUrl).pastEventsInCategory(7)

        assertThat(result).containsExactly(EXPECTED_REGISTRATIONS)
        assertThat(requests.map { it.path }).containsExactly("/token", "/event")
        assertThat(decodeQuery(requests[1].query)).isEqualTo(mapOf("categories" to "7", "onlyPast" to "true"))
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
    fun `should fetch all events in a category with event details`() {
        eventJson = """
            {
              "event": {
                "id": "$EVENT_ID",
                "title": " Security meetup ",
                "description": "Synthetic description",
                "startTime": "2026-10-03T10:00:00",
                "endTime": "2026-10-03T11:00:00",
                "location": " Oslo "
              }
            }
        """.trimIndent()

        val result = client("http://localhost:${server.address.port}").eventsInCategory(54)

        assertThat(result).containsExactly(
            DeltaEventDetails(
                id = EVENT_ID,
                title = "Security meetup",
                description = "Synthetic description",
                startTime = LocalDateTime.parse("2026-10-03T10:00:00"),
                endTime = LocalDateTime.parse("2026-10-03T11:00:00"),
                location = "Oslo",
            )
        )
        assertThat(decodeQuery(requests[1].query)).isEqualTo(mapOf("categories" to "54"))
    }

    @Test
    fun `should reject event details without an end time`() {
        assertThatThrownBy { client("http://localhost:${server.address.port}").eventsInCategory(54) }
            .isInstanceOf(DeltaIntegrationException::class.java)
            .hasMessage(DeltaFailure.INVALID_RESPONSE.summary)
    }

    @Test
    fun `should fetch a single event by id`() {
        val result = client("http://localhost:${server.address.port}").event(EVENT_ID)

        assertThat(result).isEqualTo(EXPECTED_REGISTRATIONS)
        assertThat(requests.map { it.path }).containsExactly("/token", "/event/$EVENT_ID")
    }

    @Test
    fun `should include a host only event in credit eligibility`() {
        eventJson = """
            {
              "event": {"id": "$EVENT_ID", "startTime": "2026-10-03T10:00:00"},
              "hosts": [
                {"email": " host@nav.no "},
                {"email": ""},
                {"email": null}
              ]
            }
        """.trimIndent()

        val result = client("http://localhost:${server.address.port}").event(EVENT_ID)

        assertThat(result?.participantEmails).containsExactly("host@nav.no")
    }

    @Test
    fun `should include an email only once when listed as both host and participant`() {
        eventJson = """
            {
              "event": {"id": "$EVENT_ID", "startTime": "2026-10-03T10:00:00"},
              "participants": [{"email": "participant@nav.no"}],
              "hosts": [{"email": " participant@nav.no "}]
            }
        """.trimIndent()

        val result = client("http://localhost:${server.address.port}").event(EVENT_ID)

        assertThat(result?.participantEmails).containsExactly("participant@nav.no")
    }

    @Test
    fun `should still include participants when the host roster is absent`() {
        eventJson = """
            {
              "event": {"id": "$EVENT_ID", "startTime": "2026-10-03T10:00:00"},
              "participants": [{"email": "participant@nav.no"}]
            }
        """.trimIndent()

        val result = client("http://localhost:${server.address.port}").event(EVENT_ID)

        assertThat(result?.participantEmails).containsExactly("participant@nav.no")
    }

    @Test
    fun `should return null when a single event is not found`() {
        val result = client("http://localhost:${server.address.port}").event(MISSING_EVENT_ID)

        assertThat(result).isNull()
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
            client(baseUrl).pastEventsInCategory(13)
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
        val MISSING_EVENT_ID: UUID = UUID.fromString("923e4567-e89b-12d3-a456-426614174000")
        val FULL_EVENT_JSON = """
            {
              "event": {"id": "$EVENT_ID", "startTime": "2026-10-03T10:00:00", "title": "Synthetic event"},
              "participants": [
                {"email": " participant@nav.no ", "name": "Synthetic Participant"},
                {"email": "", "name": "No Email"}
              ],
              "hosts": [{"email": "host@nav.no", "name": "Synthetic Host"}],
              "categories": []
            }
        """.trimIndent()
        val EXPECTED_REGISTRATIONS = DeltaEventRegistrations(
            EVENT_ID,
            LocalDateTime.parse("2026-10-03T10:00:00"),
            setOf("participant@nav.no", "host@nav.no"),
        )
    }
}
