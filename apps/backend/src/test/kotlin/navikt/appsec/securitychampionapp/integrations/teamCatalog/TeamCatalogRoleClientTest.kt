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
import org.mockito.kotlin.mock
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TeamCatalogRoleClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: TeamCatalogRoleClient
    private val mapper = JsonMapper.builder().build()
    private val putBodies = mutableMapOf<String, String>()
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
        observedMembershipPath = null
        observedMembershipQuery = null
        failTeamTwo = false
        releaseTeamOneResponse = null
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            val response = if (exchange.requestMethod == "GET") {
                observedMembershipPath = exchange.requestURI.path
                observedMembershipQuery = exchange.requestURI.rawQuery
                membershipResponse
            } else {
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
        client = TeamCatalogRoleClient(
            WebClient.builder().baseUrl("http://localhost:${server.address.port}").build(),
        )
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
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

    @Test
    fun `treats a timed out put as a per-team failure and continues`() {
        val releaseResponse = CountDownLatch(1)
        releaseTeamOneResponse = releaseResponse
        val timeoutClient = TeamCatalogRoleClient(
            WebClient.builder().baseUrl("http://localhost:${server.address.port}").build(),
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
