package navikt.appsec.securitychampionapp.integrations.playbook

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class PlaybookEventClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: PlaybookEventClient
    private var body = ""
    private var status = 200
    private var contentType = "application/json"

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/events.json") { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        client = PlaybookEventClient("http://localhost:${server.address.port}/events.json")
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `should read version one feed with stable IDs and date only ranges`() {
        body = feed(event("playbook:course"), event("external:conference"))

        val events = client.fetchEvents()

        assertThat(events.map { it.id }).containsExactly("playbook:course", "external:conference")
        assertThat(events.first().startDate).isEqualTo("2026-10-20")
        assertThat(events.first().endDate).isEqualTo("2026-10-22")
        assertThat(events.first().audience).isEqualTo("Alle")
    }

    @Test
    fun `should reject HTML fallback and HTTP failures`() {
        body = "<html>Homepage</html>"
        contentType = "text/html"
        assertThatThrownBy { client.fetchEvents() }.isInstanceOf(IllegalStateException::class.java)

        status = 503
        assertThatThrownBy { client.fetchEvents() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `should reject unsupported versions duplicate IDs and invalid fields`() {
        val valid = event("playbook:course")
        val invalidFeeds = listOf(
            feed(valid).replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            feed(valid, valid),
            feed(valid.replace("playbook:course", "unknown:course")),
            feed(valid.replace("2026-10-20", "2026-02-30")),
            feed(valid.replace("2026-10-22", "2026-10-19")),
            feed(valid.replace("2026-10-20", "2026-10-20T10:00:00Z")),
            feed(valid.replace("https://example.org", "javascript:alert(1)")),
            feed(valid.replace("\"Course\"", "\"\"")),
        )
        invalidFeeds.forEach { invalid ->
            body = invalid
            assertThatThrownBy { client.fetchEvents() }.isInstanceOf(Exception::class.java)
        }
    }

    @Test
    fun `should accept an empty valid snapshot`() {
        body = feed()
        assertThat(client.fetchEvents()).isEmpty()
    }

    private fun feed(vararg events: String) = """{"schemaVersion":1,"events":[${events.joinToString(",")}]}"""

    private fun event(id: String) = """
        {"id":"$id","title":"Course","startDate":"2026-10-20","endDate":"2026-10-22",
         "audience":"Alle","url":"https://example.org"}
    """.trimIndent()
}
