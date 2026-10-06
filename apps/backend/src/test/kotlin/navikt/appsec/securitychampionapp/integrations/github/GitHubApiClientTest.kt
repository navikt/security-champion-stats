package navikt.appsec.securitychampionapp.integrations.github

import com.sun.net.httpserver.HttpServer
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import tools.jackson.databind.json.JsonMapper

class GitHubApiClientTest {
    private lateinit var server: HttpServer
    private lateinit var tokens: GitHubAppTokenProvider
    private lateinit var client: GitHubApiClient
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val routes = mutableMapOf<String, String>()
    private val pageRoutes = mutableMapOf<Pair<String, Int>, String>()
    private val identityPages = ArrayDeque<String>()
    private var expireNextApiRequest = false
    private var tokenResponse = """{"token":"installation-token","expires_at":"2026-10-06T13:00:00Z"}"""
    private var observedAt = now
    private var identityStatus = 200
    private var tokenRequests = 0
    private var tokenBody = ""
    private var jwt = ""
    private val mapper = JsonMapper.builder().build()
    private val sha = "a".repeat(40)

    @BeforeEach
    fun startServer() {
        routes["/repos/navikt/security-playbook"] = """{"default_branch":"main"}"""
        routes["/repos/navikt/security-playbook/pulls"] = "[]"
        routes["/repos/navikt/security-playbook/commits"] = "[]"
        routes["/graphql"] = identities()
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            val tokenRequest = exchange.requestURI.path == "/app/installations/2/access_tokens"
            val body: String
            val status: Int
            if (tokenRequest) {
                tokenRequests++
                tokenBody = exchange.requestBody.bufferedReader().readText()
                jwt = exchange.requestHeaders.getFirst("Authorization").removePrefix("Bearer ")
                body = tokenResponse
                status = 201
            } else {
                val path = exchange.requestURI.path
                val page = exchange.requestURI.rawQuery?.split("&")
                    ?.firstOrNull { it.startsWith("page=") }?.substringAfter("=")?.toInt() ?: 1
                body = if (path == "/graphql" && identityPages.isNotEmpty()) identityPages.removeFirst()
                    else pageRoutes[path to page] ?: routes[path] ?: "[]"
                status = if (expireNextApiRequest) {
                    expireNextApiRequest = false
                    401
                } else if (path == "/graphql") identityStatus else 200
            }
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        val webClient = WebClient.builder().baseUrl("http://localhost:${server.address.port}").build()
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant() = observedAt
        }
        tokens = GitHubAppTokenProvider("1", "2", pem(), webClient, clock)
        client = GitHubApiClient(webClient, tokens)
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `should sign the App JWT scope tokens and cache until invalidated`() {
        assertThat(tokens.token()).isEqualTo("installation-token")
        assertThat(tokens.token()).isEqualTo("installation-token")
        assertThat(tokenRequests).isEqualTo(1)
        assertThat(mapper.readTree(tokenBody).path("permissions").path("members").asString()).isEqualTo("read")
        assertThat(mapper.readTree(tokenBody).path("repositories").first().asString()).isEqualTo("security-playbook")
        val parts = jwt.split(".")
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keyPair.public)
            update("${parts[0]}.${parts[1]}".toByteArray())
        }
        assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue()
        val claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]))
        assertThat(claims.path("iss").asString()).isEqualTo("1")
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(600)
        tokens.invalidate()
        tokens.token()
        assertThat(tokenRequests).isEqualTo(2)
    }

    @Test
    fun `should refresh an expiring token and retry an unauthorized API request only once`() {
        tokens.token()
        observedAt = now.plusSeconds(3541)
        tokenResponse = """{"token":"new-token","expires_at":"2026-10-06T14:00:00Z"}"""
        assertThat(tokens.token()).isEqualTo("new-token")
        assertThat(tokenRequests).isEqualTo(2)
        expireNextApiRequest = true
        assertThat(client.identities()).hasSize(1)
        assertThat(tokenRequests).isEqualTo(3)
        identityStatus = 401
        assertThatThrownBy { client.identities() }.hasMessage(GitHubFailure.ACCESS.summary)
        assertThat(tokenRequests).isEqualTo(4)
    }

    @Test
    fun `should sign using GitHub PKCS one PEM and escaped multiline secrets`() {
        val key = keyPair.private as RSAPrivateCrtKey
        val values = listOf(
            BigInteger.ZERO, key.modulus, key.publicExponent, key.privateExponent,
            key.primeP, key.primeQ, key.primeExponentP, key.primeExponentQ, key.crtCoefficient,
        )
        val encoded = der(0x30, values.flatMap { der(0x02, it.toByteArray()).toList() }.toByteArray())
        val pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\\n" + // gitleaks:allow runtime-generated test key
            Base64.getEncoder().encodeToString(encoded) + "\\n-----END RSA PRIVATE KEY-----"
        val webClient = WebClient.builder().baseUrl("http://localhost:${server.address.port}").build()
        val provider = GitHubAppTokenProvider("1", "2", pkcs1, webClient, Clock.fixed(now, ZoneOffset.UTC))
        assertThat(provider.token()).isEqualTo("installation-token")
        val parts = jwt.split(".")
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keyPair.public)
            update("${parts[0]}.${parts[1]}".toByteArray())
        }
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue()
    }

    @Test
    fun `should follow identity cursors and REST pages instead of truncating to the first hundred PRs`() {
        identityPages += identities().replace(
            """"hasNextPage":false,"endCursor":null""", """"hasNextPage":true,"endCursor":"next"""",
        )
        identityPages += identities().replace("10", "11").replace("person", "other")
        assertThat(client.identities().map { it.accountId }).containsExactly(10, 11)
        val path = "/repos/navikt/security-playbook/pulls"
        pageRoutes[path to 1] = (1..100).joinToString(",", "[", "]") { pr(it) }
        pageRoutes[path to 2] = "[${pr(101)}]"
        assertThat(client.contributions(now.minusSeconds(3600), now)).hasSize(101)
    }

    @Test
    fun `should map only linked SAML identities and fail closed on GraphQL errors or unavailable identities`() {
        assertThat(client.identities()).containsExactly(GitHubIdentity(10, "person", "person@nav.no"))
        listOf("""{"errors":[{"message":"private data"}]}""", """{"data":{"organization":null}}""").forEach {
            routes["/graphql"] = it
            assertThatThrownBy { client.identities() }
                .isInstanceOf(GitHubIntegrationException::class.java)
                .hasMessage(GitHubFailure.IDENTITY.summary)
        }
        identityStatus = 403
        assertThatThrownBy { client.identities() }.hasMessage(GitHubFailure.ACCESS.summary)
    }

    @Test
    fun `should reject ambiguous SAML accounts instead of choosing an identity`() {
        routes["/graphql"] = identities(
            """{"user":{"databaseId":10,"login":"person"},"samlIdentity":{"nameId":"person@nav.no"}},
                {"user":{"databaseId":11,"login":"other"},"samlIdentity":{"nameId":"person@nav.no"}}""",
        )
        assertThatThrownBy { client.identities() }.hasMessage(GitHubFailure.IDENTITY.summary)
    }

    @Test
    fun `should credit merged PRs and standalone commits but suppress all merged PR commit representations`() {
        routes["/repos/navikt/security-playbook/pulls"] = """
            [${pr(1)},${pr(2, "Bot")},{"merged_at":null}]
        """.trimIndent()
        routes["/repos/navikt/security-playbook/commits"] = """[${commit(sha)}]"""
        val windowStart = now.minusSeconds(3600)

        val standalone = client.contributions(windowStart, now)
        assertThat(standalone.map { it.type })
            .containsExactly(ActivityCreditType.GITHUB_PULL_REQUEST, ActivityCreditType.GITHUB_COMMIT)
        assertThat(standalone.last().occurredAt).isEqualTo(Instant.parse("2026-10-06T11:30:00Z"))

        routes["/repos/navikt/security-playbook/commits/$sha/pulls"] = """[${pr(1)}]"""
        assertThat(client.contributions(windowStart, now).map { it.type })
            .containsExactly(ActivityCreditType.GITHUB_PULL_REQUEST)
    }

    @Test
    fun `should suppress merge squash and rebased commits including those belonging to bot PRs`() {
        val shas = listOf("a".repeat(40), "b".repeat(40), "c".repeat(40), "d".repeat(40))
        routes["/repos/navikt/security-playbook/commits"] = shas.joinToString(",", "[", "]") { commit(it) }
        shas.forEachIndexed { index, sha ->
            routes["/repos/navikt/security-playbook/commits/$sha/pulls"] = "[${pr(index + 1, "Bot")}]"
        }
        assertThat(client.contributions(now.minusSeconds(3600), now)).isEmpty()
    }

    @Test
    fun `should not count PRs targeting other branches and should exclude automated committer activity`() {
        routes["/repos/navikt/security-playbook/pulls"] = "[${pr(1).replace("main", "feature")}]"
        routes["/repos/navikt/security-playbook/commits"] = """[${commit(sha).replace(
            """"committer":${user()}""", """"committer":${user("Bot")}""",
        )}]"""
        assertThat(client.contributions(now.minusSeconds(3600), now)).isEmpty()
    }

    @Test
    fun `should exclude bots unassociated authors and activity outside the window`() {
        routes["/repos/navikt/security-playbook/commits"] = """[${commit(sha, "Bot")}]"""
        assertThat(client.contributions(now.minusSeconds(3600), now)).isEmpty()
        routes["/repos/navikt/security-playbook/commits"] = """[${commit(sha).replace(user(), "null")}]"""
        assertThat(client.contributions(now.minusSeconds(3600), now)).isEmpty()
        routes["/repos/navikt/security-playbook/pulls"] = """[${pr(1)}]"""
        routes["/repos/navikt/security-playbook/commits"] = "[]"
        assertThat(client.contributions(now, now.plusSeconds(3600))).isEmpty()
    }

    @Test
    fun `should reject invalid private keys without exposing their contents`() {
        val webClient = WebClient.builder().baseUrl("http://localhost:${server.address.port}").build()
        val invalid = GitHubAppTokenProvider(
            "1", "2", "secret-invalid-key", webClient, Clock.fixed(now, ZoneOffset.UTC),
        )
        assertThatThrownBy { invalid.token() }.hasMessage(GitHubFailure.CONFIGURATION.summary)
        assertThat(tokenRequests).isZero()
    }

    private fun pem() = "-----BEGIN PRIVATE KEY-----\n" + // gitleaks:allow runtime-generated test key
        Base64.getEncoder().encodeToString(keyPair.private.encoded) + "\n-----END PRIVATE KEY-----"

    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val length = if (bytes.size < 128) byteArrayOf(bytes.size.toByte())
            else byteArrayOf(0x82.toByte(), (bytes.size shr 8).toByte(), bytes.size.toByte())
        return byteArrayOf(tag.toByte()) + length + bytes
    }

    private fun user(type: String = "User") = """{"id":10,"login":"person","type":"$type"}"""

    private fun pr(number: Int, type: String = "User") =
        """{"number":$number,"merged_at":"2026-10-06T11:30:00Z","user":${user(type)},"base":{"ref":"main"}}"""

    private fun commit(sha: String, type: String = "User") =
        """{"sha":"$sha","author":${user(type)},"committer":${user()},
            "commit":{"committer":{"date":"2026-10-06T11:30:00Z"}}}"""

    private fun identities(
        nodes: String = """{"user":{"databaseId":10,"login":"person"},"samlIdentity":{"nameId":"person@nav.no"}}""",
    ) = """{"data":{"organization":{"samlIdentityProvider":{"externalIdentities":{
        "nodes":[$nodes],"pageInfo":{"hasNextPage":false,"endCursor":null}}}}}}"""
}
