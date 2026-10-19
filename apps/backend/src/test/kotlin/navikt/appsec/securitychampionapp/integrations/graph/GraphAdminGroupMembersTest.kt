package navikt.appsec.securitychampionapp.integrations.graph

import com.sun.net.httpserver.HttpServer
import navikt.appsec.securitychampionapp.app.scoring.AdminGroupUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

class GraphAdminGroupMembersTest {
    private lateinit var server: HttpServer
    private lateinit var source: GraphAdminGroupMembers
    private lateinit var baseUrl: String
    private val requests = CopyOnWriteArrayList<String>()
    private var now = Instant.parse("2026-10-19T10:00:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
    private var firstPage = """{"value":[{"@odata.type":"#microsoft.graph.user","userPrincipalName":" ADMIN@nav.no "}]}"""
    private var secondPage = """{"value":[{"@odata.type":"#microsoft.graph.user","userPrincipalName":"second@nav.no"}]}"""
    private var graphStatus = 200
    private var tokenStatus = 200

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.toString()
            val (status, body) = if (exchange.requestURI.path == "/token") {
                val form = URLDecoder.decode(exchange.requestBody.bufferedReader().readText(), Charsets.UTF_8)
                assertThat(form).contains("identity_provider=entra_id", "target=https://graph.microsoft.com/.default")
                tokenStatus to """{"access_token":"graph-test-token"}"""
            } else {
                assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer graph-test-token")
                graphStatus to if (exchange.requestURI.rawQuery.contains("skiptoken")) secondPage else firstPage
            }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://localhost:${server.address.port}"
        source = GraphAdminGroupMembers(
            WebClient.create(), WebClient.create(), "$baseUrl/v1.0", "$baseUrl/token", "admin-group", clock,
        )
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `reads all direct user members and caches a complete normalized snapshot`() {
        firstPage = """
            {"value":[
                {"@odata.type":"#microsoft.graph.user","userPrincipalName":" ADMIN@nav.no "},
                {"@odata.type":"#microsoft.graph.group","id":"nested-group"}
            ],"@odata.nextLink":"$baseUrl/v1.0/groups/admin-group/members?skiptoken=next"}
        """.trimIndent()

        assertThat(source.emails()).containsExactlyInAnyOrder("admin@nav.no", "second@nav.no")
        assertThat(source.emails()).containsExactlyInAnyOrder("admin@nav.no", "second@nav.no")
        assertThat(requests).hasSize(3)
        assertThat(requests[1]).startsWith("/v1.0/groups/admin-group/members?")
        now = now.plusSeconds(300)
        firstPage = """{"value":[]}"""
        assertThat(source.emails()).isEmpty()
        assertThat(requests).hasSize(5)
    }

    @Test
    fun `failed refresh does not return expired members and is retried`() {
        assertThat(source.emails()).containsExactly("admin@nav.no")
        now = now.plusSeconds(300)
        graphStatus = 403
        assertThatThrownBy { source.emails() }.isInstanceOf(AdminGroupUnavailableException::class.java)
        graphStatus = 200
        firstPage = """{"value":[]}"""
        assertThat(source.emails()).isEmpty()
    }

    @Test
    fun `failed later page does not cache partial membership`() {
        firstPage = """{"value":[{"@odata.type":"#microsoft.graph.user","userPrincipalName":"first@nav.no"}],
            "@odata.nextLink":"$baseUrl/v1.0/groups/admin-group/members?skiptoken=next"}"""
        secondPage = """{"error":"unavailable"}"""
        assertThatThrownBy { source.emails() }.isInstanceOf(AdminGroupUnavailableException::class.java)
        secondPage = """{"value":[]}"""
        assertThat(source.emails()).containsExactly("first@nav.no")
        assertThat(requests.count { it == "/token" }).isEqualTo(2)
    }

    @Test
    fun `rejects limited user information malformed pages and token failures`() {
        for (body in listOf(
            """{"value":[{"@odata.type":"#microsoft.graph.user","userPrincipalName":null}]}""",
            """{"value":[{"@odata.type":"#microsoft.graph.user","userPrincipalName":123}]}""",
            """{"value":null}""",
            """{"value":[{"id":"missing-type"}]}""",
            """{"value":[],"@odata.nextLink":""}""",
            """not-json""",
        )) {
            firstPage = body
            assertThatThrownBy { source.emails() }.isInstanceOf(AdminGroupUnavailableException::class.java)
        }
        tokenStatus = 403
        assertThatThrownBy { source.emails() }.isInstanceOf(AdminGroupUnavailableException::class.java)
    }

    @Test
    fun `does not follow foreign resource or cyclic pagination links`() {
        for (link in listOf(
            "https://example.invalid/v1.0/groups/admin-group/members?skiptoken=next",
            "$baseUrl/v1.0/users?skiptoken=next",
            "$baseUrl/v1.0/groups/admin-group/members?skiptoken=next",
        )) {
            firstPage = """{"value":[],"@odata.nextLink":"$link"}"""
            secondPage = firstPage
            requests.clear()
            assertThatThrownBy { source.emails() }.isInstanceOf(AdminGroupUnavailableException::class.java)
            assertThat(requests).allMatch { it == "/token" || it.startsWith("/v1.0/groups/admin-group/members?") }
            assertThat(requests.size).isLessThanOrEqualTo(3)
        }
    }
}
