package navikt.appsec.securitychampionapp.integrations.github

import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64

class GitHubAppTokenProvider(
    private val appId: String,
    private val installationId: String,
    private val privateKey: String,
    private val client: WebClient,
    private val clock: Clock,
) {
    private var cachedToken: String? = null
    private var expiresAt: Instant = Instant.MIN

    @Synchronized
    fun token(): String {
        val now = clock.instant()
        cachedToken?.takeIf { expiresAt.isAfter(now.plusSeconds(60)) }?.let { return it }
        val jwt = signedJwt(now)
        val response = try {
            client.post()
                .uri("/app/installations/{id}/access_tokens", installationId)
                .headers { it.setBearerAuth(jwt) }
                .bodyValue(
                    mapOf(
                        "repositories" to listOf("security-playbook"),
                        "permissions" to mapOf("contents" to "read", "pull_requests" to "read", "members" to "read"),
                    ),
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) {
                    Mono.error(GitHubIntegrationException(GitHubFailure.TOKEN))
                }
                .bodyToMono<JsonNode>()
                .block()
                ?: throw GitHubIntegrationException(GitHubFailure.TOKEN)
        } catch (_: WebClientRequestException) {
            throw GitHubIntegrationException(GitHubFailure.TOKEN)
        } catch (_: DecodingException) {
            throw GitHubIntegrationException(GitHubFailure.TOKEN)
        }
        val token = response.path("token").asString("").takeIf { response.path("token").isString && it.isNotBlank() }
            ?: throw GitHubIntegrationException(GitHubFailure.TOKEN)
        val expiry = try {
            Instant.parse(response.path("expires_at").asString(""))
        } catch (_: java.time.format.DateTimeParseException) {
            throw GitHubIntegrationException(GitHubFailure.TOKEN)
        }
        if (!expiry.isAfter(now.plusSeconds(60))) throw GitHubIntegrationException(GitHubFailure.TOKEN)
        expiresAt = expiry
        cachedToken = token
        return token
    }

    @Synchronized
    fun invalidate() {
        cachedToken = null
        expiresAt = Instant.MIN
    }

    private fun signedJwt(now: Instant): String {
        if (!appId.matches(Regex("[1-9][0-9]*")) || !installationId.matches(Regex("[1-9][0-9]*"))) {
            throw GitHubIntegrationException(GitHubFailure.CONFIGURATION)
        }
        val header = encode("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claims = encode(
            """{"iss":"$appId","iat":${now.epochSecond - 60},"exp":${now.epochSecond + 540}}""".toByteArray(),
        )
        val unsigned = "$header.$claims"
        val signature = try {
            val pem = privateKey.replace("\\n", "\n").trim()
            val pkcs1 = pem.startsWith("-----BEGIN RSA PRIVATE KEY-----")
            val label = if (pkcs1) "RSA PRIVATE KEY" else "PRIVATE KEY"
            if (!pem.startsWith("-----BEGIN $label-----") || !pem.endsWith("-----END $label-----")) {
                throw GitHubIntegrationException(GitHubFailure.CONFIGURATION)
            }
            val bytes = Base64.getDecoder().decode(
                pem.removePrefix("-----BEGIN $label-----").removeSuffix("-----END $label-----")
                    .filterNot(Char::isWhitespace),
            )
            val encoded = if (pkcs1) {
                // Wrap GitHub's PKCS#1 RSA key in a PKCS#8 PrivateKeyInfo for the JDK key factory.
                val algorithm = byteArrayOf(
                    0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
                    0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00,
                )
                der(0x30, byteArrayOf(0x02, 0x01, 0x00) + algorithm + der(0x04, bytes))
            } else {
                bytes
            }
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(encoded))
            Signature.getInstance("SHA256withRSA").run {
                initSign(key)
                update(unsigned.toByteArray(Charsets.US_ASCII))
                sign()
            }
        } catch (_: java.security.GeneralSecurityException) {
            throw GitHubIntegrationException(GitHubFailure.CONFIGURATION)
        } catch (_: IllegalArgumentException) {
            throw GitHubIntegrationException(GitHubFailure.CONFIGURATION)
        }
        return "$unsigned.${encode(signature)}"
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val length = when {
            bytes.size < 128 -> byteArrayOf(bytes.size.toByte())
            bytes.size < 256 -> byteArrayOf(0x81.toByte(), bytes.size.toByte())
            bytes.size < 65536 -> byteArrayOf(0x82.toByte(), (bytes.size shr 8).toByte(), bytes.size.toByte())
            else -> throw GitHubIntegrationException(GitHubFailure.CONFIGURATION)
        }
        return byteArrayOf(tag.toByte()) + length + bytes
    }
}
