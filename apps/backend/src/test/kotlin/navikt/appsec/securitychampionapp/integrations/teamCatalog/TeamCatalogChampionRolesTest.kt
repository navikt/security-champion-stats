package navikt.appsec.securitychampionapp.integrations.teamCatalog

import navikt.appsec.securitychampionapp.app.membership.ChampionRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.core.env.StandardEnvironment
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class TeamCatalogChampionRolesTest {
    @Test
    fun `role in any active team suppresses announcements including teams outside product areas`() {
        val body = """
            {"paged":false,"pages":1,"totalElements":2,"content":[
              {"id":"team-1","name":"First","members":[
                {"roles":["DEVELOPER"],"resource":{"navIdent":"A12345","fullName":"Person","email":"person@nav.no"}},
                {"roles":[],"resource":{"navIdent":"B12345","fullName":"Other","email":"other@nav.no"}}
              ]},
              {"id":"team-2","name":"Second","members":[
                {"roles":["SECURITY_CHAMPION"],"resource":{"navIdent":"A12345","fullName":"Person","email":"PERSON@nav.no"}}
              ]}
            ]}
        """.trimIndent()
        val client = WebClient.builder().baseUrl("http://team-catalog").exchangeFunction { request ->
            assertThat(request.url().path).isEqualTo("/team")
            assertThat(request.url().query).isEqualTo("status=ACTIVE")
            Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build())
        }.build()
        val source = TeamCatalogChampionRoles(client, mock(), StandardEnvironment())

        assertThat(source.fetchRoles()).containsExactlyInAnyOrderEntriesOf(
            mapOf("person@nav.no" to ChampionRole.PRESENT, "other@nav.no" to ChampionRole.ABSENT),
        )
    }

    @Test
    fun `incomplete responses cannot be interpreted as missing roles`() {
        val client = WebClient.builder().exchangeFunction {
            Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body("""{"paged":true,"pages":2,"totalElements":2,"content":[]}""").build())
        }.build()
        val source = TeamCatalogChampionRoles(client, mock(), StandardEnvironment())

        assertThatThrownBy { source.fetchRoles() }.hasMessageContaining("incomplete")
    }

    @Test
    fun `failed requests and empty catalogues do not return an absence snapshot`() {
        listOf(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.OK).forEach { status ->
            val client = WebClient.builder().exchangeFunction {
                Mono.just(ClientResponse.create(status).header("Content-Type", "application/json")
                    .body("""{"paged":false,"pages":1,"totalElements":0,"content":[]}""").build())
            }.build()

            assertThatThrownBy { TeamCatalogChampionRoles(client, mock(), StandardEnvironment()).fetchRoles() }
                .isInstanceOf(RuntimeException::class.java)
        }

        @Test
        fun `unpaged team endpoint includes roles beyond typical page sizes with one request`() {
            val teams = (1..500).joinToString(",") { index ->
                """{"id":"team-$index","name":"Team $index","members":[
                    {"roles":["${if (index == 500) "SECURITY_CHAMPION" else "DEVELOPER"}"],
                     "resource":{"navIdent":"A12345","fullName":"Person","email":"person@nav.no"}}]}"""
            }
            var calls = 0
            val client = WebClient.builder().exchangeFunction { request ->
                calls++
                assertThat(request.url().query).isEqualTo("status=ACTIVE")
                Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("""{"paged":false,"pages":1,"totalElements":500,"content":[$teams]}""").build())
            }.build()
            assertThat(TeamCatalogChampionRoles(client, mock(), StandardEnvironment()).fetchRoles())
                .containsEntry("person@nav.no", ChampionRole.PRESENT)
            assertThat(calls).isEqualTo(1)
        }

        @Test
        fun `even a single page is rejected if the unpaged contract changes`() {
            val client = WebClient.builder().exchangeFunction {
                Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("""{"paged":true,"pages":1,"totalElements":0,"content":[]}""").build())
            }.build()
            assertThatThrownBy { TeamCatalogChampionRoles(client, mock(), StandardEnvironment()).fetchRoles() }
                .hasMessageContaining("incomplete")
        }
    }
}
