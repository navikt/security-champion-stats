package navikt.appsec.securitychampionapp.integrations.teamCatalog

import com.sun.net.httpserver.HttpServer
import navikt.appsec.securitychampionapp.app.membership.InvalidTeamCatalogSelectionException
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleOperation
import navikt.appsec.securitychampionapp.app.membership.TeamCatalogRoleUpdateStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class TeamCatalogRoleClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: TeamCatalogRoleClient
    private lateinit var tokenProvider: TeamCatalogOboTokenProvider
    private val mapper = JsonMapper.builder().build()
    private val putBodies = mutableMapOf<String, String>()
    private val tokenRequests = mutableListOf<Map<String, String>>()
    private val putAuthorizations = mutableListOf<String?>()
    private var tokenResponseStatus = 200
    private var tokenResponseBody =
        """{"access_token":"team-catalog-user-token","token_type":"Bearer","expires_in":3600}"""
    private var observedMembershipPath: String? = null
    private var observedMembershipQuery: String? = null
    private var failTeamTwo = false
    private var releaseTeamOneResponse: CountDownLatch? = null
    private val membershipResponse = """
        {
          "teams": [
            {
              "id": "team-1",
              "name": "Alpha",
              "status": "ACTIVE",
              "description": "Keep this description",
              "slackChannel": "alpha-channel",
              "productAreaId": "area-1",
              "teamOwnerIdent": "C12345",
              "clusterIds": ["cluster-1"],
              "teamType": "STREAM_ALIGNED",
              "naisTeams": ["alpha-app"],
              "contactAddresses": [{"address": "alpha@nav.no", "type": "EPOST"}],
              "tags": ["existing-tag"],
              "officeHours": {
                "location": {"code": "OSL"},
                "days": ["MONDAY"],
                "information": "Keep office hours"
              },
              "members": [
                {
                  "navIdent": "A12345",
                  "roles": ["DEVELOPER"],
                  "description": "Target member",
                  "teamPercent": 100,
                  "startDate": "2024-01-01",
                  "resource": {"navIdent": "A12345", "email": "person@nav.no"}
                },
                {
                  "navIdent": "B12345",
                  "roles": ["TECH_LEAD"],
                  "teamPercent": 50,
                  "resource": {"navIdent": "B12345", "email": "other@nav.no"}
                }
              ]
            },
            {
              "id": "team-2",
              "name": "Beta",
              "status": "ACTIVE",
              "members": [
                {"navIdent": "A12345", "roles": ["SECURITY_CHAMPION", "DEVELOPER"]}
              ]
            },
            {
              "id": "team-3",
              "name": "Inactive",
              "status": "INACTIVE",
              "members": [{"navIdent": "A12345", "roles": []}]
            }
          ]
        }
    """.trimIndent()

    @BeforeEach
    fun startServer() {
        putBodies.clear()
        tokenRequests.clear()
        putAuthorizations.clear()
        observedMembershipPath = null
        observedMembershipQuery = null
        failTeamTwo = false
        releaseTeamOneResponse = null
        authenticateWith("backend-user-token")
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            val response = if (exchange.requestURI.path == "/token/exchange") {
                tokenRequests.add(
                    exchange.requestBody.bufferedReader().readText().split("&").associate { field ->
                        val parts = field.split("=", limit = 2)
                        URLDecoder.decode(parts[0], Charsets.UTF_8) to
                            URLDecoder.decode(parts[1], Charsets.UTF_8)
                    },
                )
                if (tokenResponseStatus != 200) {
                    exchange.sendResponseHeaders(tokenResponseStatus, -1)
                    exchange.close()
                    return@createContext
                }
                tokenResponseBody
            } else if (exchange.requestMethod == "GET") {
                observedMembershipPath = exchange.requestURI.path
                observedMembershipQuery = exchange.requestURI.rawQuery
                membershipResponse
            } else {
                val authorization = exchange.requestHeaders.getFirst("Authorization")
                putAuthorizations.add(authorization)
                if (authorization != "Bearer team-catalog-user-token") {
                    exchange.sendResponseHeaders(403, -1)
                    exchange.close()
                    return@createContext
                }
                val teamId = exchange.requestURI.path.substringAfterLast("/")
                putBodies[teamId] = exchange.requestBody.bufferedReader().readText()
                if (teamId == "team-1") {
                    releaseTeamOneResponse?.let {
                        check(it.await(5, TimeUnit.SECONDS))
                        exchange.close()
                        return@createContext
                    }
                }
                if (failTeamTwo && teamId == "team-2") {
                    exchange.sendResponseHeaders(503, -1)
                    return@createContext
                }
                """{"id":"$teamId"}"""
            }
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        tokenProvider = TeamCatalogOboTokenProvider(
            WebClient.builder().build(),
            "http://localhost:${server.address.port}/token/exchange",
            "prod-gcp:org:team-catalog-backend",
        )
        client = TeamCatalogRoleClient(
            WebClient.builder().baseUrl("http://localhost:${server.address.port}").build(),
            tokenProvider,
        )
    }

    private fun authenticateWith(credentials: Any?) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("person@nav.no", credentials, emptyList())
    }

    @AfterEach
    fun stopServer() {
        SecurityContextHolder.clearContext()
        server.stop(0)
        (server.executor as ExecutorService).shutdownNow()
    }

    @Test
    fun `returns current active memberships for the authenticated navIdent`() {
        assertThat(client.memberships("person@nav.no", "A12345"))
            .containsExactly(
                navikt.appsec.securitychampionapp.app.membership.TeamCatalogTeamMembership(
                    "team-1", "Alpha", false,
                ),
                navikt.appsec.securitychampionapp.app.membership.TeamCatalogTeamMembership(
                    "team-2", "Beta", true,
                ),
            )
        assertThat(observedMembershipPath).isEqualTo("/member/membership/byUserEmail")
        assertThat(observedMembershipQuery).isEqualTo("email=person@nav.no")
        assertThat(putBodies).isEmpty()
        assertThat(tokenRequests).isEmpty()
    }

    @Test
    fun `updates selected roles while preserving complete team request data`() {
        val result = client.saveSelection(
            "person@nav.no",
            "A12345",
            selectedTeamIds = setOf("team-1"),
            writeEnabled = true,
        )

        assertThat(result.updates.map { it.status })
            .containsExactly(TeamCatalogRoleUpdateStatus.UPDATED, TeamCatalogRoleUpdateStatus.UPDATED)
        assertThat(result.updates.map { it.operation })
            .containsExactly(TeamCatalogRoleOperation.ADD, TeamCatalogRoleOperation.REMOVE)
        assertThat(putBodies.keys).containsExactlyInAnyOrder("team-1", "team-2")
        assertThat(tokenRequests).containsExactly(
            mapOf(
                "identity_provider" to "entra_id",
                "target" to "prod-gcp:org:team-catalog-backend",
                "user_token" to "backend-user-token",
            ),
        )

        val alpha = mapper.readTree(putBodies.getValue("team-1"))
        assertThat(alpha.path("description").asString()).isEqualTo("Keep this description")
        assertThat(alpha.path("slackChannel").asString()).isEqualTo("alpha-channel")
        assertThat(alpha.path("contactAddresses").get(0).path("address").asString()).isEqualTo("alpha@nav.no")
        assertThat(alpha.path("clusterIds").get(0).asString()).isEqualTo("cluster-1")
        assertThat(alpha.path("tags").get(0).asString()).isEqualTo("existing-tag")
        assertThat(alpha.path("officeHours").path("locationCode").asString()).isEqualTo("OSL")
        assertThat(alpha.path("members").size()).isEqualTo(2)
        assertThat(alpha.path("members").get(0).path("roles").toString())
            .isEqualTo("""["DEVELOPER","SECURITY_CHAMPION"]""")
        assertThat(alpha.path("members").get(1).path("roles").get(0).asString()).isEqualTo("TECH_LEAD")

        val beta = mapper.readTree(putBodies.getValue("team-2"))
        assertThat(beta.path("members").get(0).path("roles").toString()).isEqualTo("""["DEVELOPER"]""")
        assertThat(result.updates.first().payloadPreview.currentMemberRoles)
            .containsExactly("DEVELOPER", "SECURITY_CHAMPION")
        assertThat(result.updates.first().payloadPreview.toString()).doesNotContain("A12345", "person@nav.no")
    }

    @Test
    fun `dry run previews changes without sending a put and rejects foreign team IDs`() {
        val preview = client.saveSelection(
            "person@nav.no",
            "A12345",
            selectedTeamIds = setOf("team-1"),
            writeEnabled = false,
        )
        assertThat(preview.updates.map { it.status })
            .containsExactly(TeamCatalogRoleUpdateStatus.PREVIEW, TeamCatalogRoleUpdateStatus.PREVIEW)
        assertThat(putBodies).isEmpty()
        assertThat(tokenRequests).isEmpty()

        assertThatThrownBy {
            client.saveSelection("person@nav.no", "A12345", setOf("not-a-membership"), false)
        }.isInstanceOf(InvalidTeamCatalogSelectionException::class.java)
        assertThat(putBodies).isEmpty()
    }

    @Test
    fun `reports per-team failures and continues with other selected teams`() {
        failTeamTwo = true
        val result = client.saveSelection(
            "person@nav.no",
            "A12345",
            selectedTeamIds = setOf("team-1"),
            writeEnabled = true,
        )

        assertThat(result.updates.map { it.status })
            .containsExactly(TeamCatalogRoleUpdateStatus.UPDATED, TeamCatalogRoleUpdateStatus.FAILED)
        assertThat(putBodies.keys).containsExactlyInAnyOrder("team-1", "team-2")
    }

    @ParameterizedTest
    @ValueSource(strings = ["http-error", "empty", "missing-token", "blank-token", "malformed"])
    fun `failed token exchanges fail all planned updates without sending a put`(failure: String) {
        when (failure) {
            "http-error" -> tokenResponseStatus = 403
            "empty" -> tokenResponseBody = ""
            "missing-token" -> tokenResponseBody = "{}"
            "blank-token" -> tokenResponseBody = """{"access_token":" "}"""
            "malformed" -> tokenResponseBody = "not-json"
        }

        val result = client.saveSelection("person@nav.no", "A12345", setOf("team-1"), true)

        assertThat(result.updates.map { it.status })
            .containsExactly(TeamCatalogRoleUpdateStatus.FAILED, TeamCatalogRoleUpdateStatus.FAILED)
        assertThat(tokenRequests).hasSize(1)
        assertThat(putAuthorizations).isEmpty()
        assertThat(result.toString()).doesNotContain("backend-user-token", "team-catalog-user-token")
    }

    @ParameterizedTest
    @ValueSource(strings = ["no-authentication", "null", "blank"])
    fun `missing user credentials fail closed without exchanging or writing`(credentials: String) {
        when (credentials) {
            "no-authentication" -> SecurityContextHolder.clearContext()
            "null" -> authenticateWith(null)
            "blank" -> authenticateWith(" ")
        }

        val result = client.saveSelection("person@nav.no", "A12345", setOf("team-1"), true)

        assertThat(result.updates.map { it.status })
            .containsExactly(TeamCatalogRoleUpdateStatus.FAILED, TeamCatalogRoleUpdateStatus.FAILED)
        assertThat(tokenRequests).isEmpty()
        assertThat(putAuthorizations).isEmpty()
    }

    @Test
    fun `unchanged selections do not exchange a token or write`() {
        val result = client.saveSelection("person@nav.no", "A12345", setOf("team-2"), true)

        assertThat(result.updates).isEmpty()
        assertThat(tokenRequests).isEmpty()
        assertThat(putAuthorizations).isEmpty()
    }

    @Test
    fun `singleton client exchanges the current users token instead of reusing previous credentials`() {
        client.saveSelection("person@nav.no", "A12345", setOf("team-1"), true)
        authenticateWith("next-backend-user-token")

        val result = client.saveSelection("person@nav.no", "A12345", setOf("team-1"), true)

        assertThat(result.updates.map { it.status })
            .containsExactly(TeamCatalogRoleUpdateStatus.UPDATED, TeamCatalogRoleUpdateStatus.UPDATED)
        assertThat(tokenRequests.map { it["user_token"] })
            .containsExactly("backend-user-token", "next-backend-user-token")
        assertThat(putAuthorizations).containsOnly("Bearer team-catalog-user-token")
    }

    @Test
    fun `treats a timed out put as a per-team failure and continues`() {
        val releaseResponse = CountDownLatch(1)
        releaseTeamOneResponse = releaseResponse
        val timeoutClient = TeamCatalogRoleClient(
            WebClient.builder().baseUrl("http://localhost:${server.address.port}").build(),
            tokenProvider,
            Duration.ofMillis(300),
        )

        try {
            val result = timeoutClient.saveSelection("person@nav.no", "A12345", setOf("team-1"), true)

            assertThat(result.updates.map { it.status })
                .containsExactly(TeamCatalogRoleUpdateStatus.FAILED, TeamCatalogRoleUpdateStatus.UPDATED)
            assertThat(putBodies.keys).contains("team-2")
        } finally {
            releaseResponse.countDown()
        }
    }
}
