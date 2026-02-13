package navikt.appsec.securitychampionstats.common

import navikt.appsec.securitychampionstats.common.teamCatalog.TeamCatalog
import navikt.appsec.securitychampionstats.common.teamCatalog.dto.MemberResponse
import navikt.appsec.securitychampionstats.common.teamCatalog.dto.ProductArea
import navikt.appsec.securitychampionstats.common.teamCatalog.dto.ProductAreaResponse
import navikt.appsec.securitychampionstats.common.teamCatalog.dto.ResourceResponse
import navikt.appsec.securitychampionstats.common.teamCatalog.dto.TeamCatalogTeam
import navikt.appsec.securitychampionstats.common.teamCatalog.dto.TeamResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.springframework.web.reactive.function.client.WebClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@SpringBootTest(classes = [TeamCatalog::class])
@ActiveProfiles("test")
class TeamCatalogTest {

    @MockitoBean lateinit var externalServiceWebClient: WebClient


    lateinit var getSpec: RequestHeadersUriSpec<*>
    lateinit var headersSpec: RequestHeadersSpec<*>
    lateinit var responseSpec: ResponseSpec

    @BeforeEach
    fun setup() {
        getSpec = mock(RequestHeadersUriSpec::class.java) as RequestHeadersUriSpec<*>
        headersSpec = mock(RequestHeadersSpec::class.java) as RequestHeadersSpec<*>
        responseSpec = mock(ResponseSpec::class.java)

        whenever(externalServiceWebClient.get()).thenReturn(getSpec)
        whenever(getSpec.uri(any<String>())).thenReturn(headersSpec)
        whenever(headersSpec.retrieve()).thenReturn(responseSpec)
    }

    @Test
    fun `fetchMembersWithRole returns security champions on successful response`() {
        val productAreaResponse = Mono.just(ProductAreaResponse(listOf(ProductArea("1", "Product A"))))
        val teamResponse: Mono<TeamResponse> = Mono.just(TeamResponse(listOf(
            TeamCatalogTeam(
                members = listOf(
                    MemberResponse(
                        roles = listOf("SECURITY_CHAMPION"),
                        resource = ResourceResponse(
                            navIdent = "Z123456",
                            fullName = "Test User",
                            email = "test@email.test"
                        )
                    ),
                    MemberResponse(
                        roles = listOf("DEVELOPER"),
                        resource = ResourceResponse(
                            navIdent = "Z654321",
                            fullName = "Test User 2",
                            email = "test2@email.test"
                        )
                    ),
                    MemberResponse(
                        roles = listOf("SECURITY_CHAMPION"),
                        resource = ResourceResponse(
                            navIdent = "Z112233",
                            fullName = "Test User 3",
                            email = "test3@email.test"
                        )
                    )
                )
            )
        )))

        whenever(responseSpec.onStatus(any(), any())).thenReturn(responseSpec)
        whenever(
            responseSpec.bodyToMono(any<ParameterizedTypeReference<ProductAreaResponse>>()
        )).thenReturn(productAreaResponse)
        whenever(responseSpec.bodyToMono<TeamResponse>()).thenReturn(teamResponse)

        val teamCatalog = TeamCatalog(externalServiceWebClient)
        val result = teamCatalog.fetchMembersWithRole()
        assertEquals(2, result.size)
        assertEquals("Z112233", result.last().navIdent)
    }

    @Test
    fun `fetchMembersWithRole returns empty list on error response`() {
        whenever(responseSpec.onStatus(any(), any())).thenReturn(responseSpec)
        whenever(
            responseSpec.bodyToMono(any<ParameterizedTypeReference<ProductAreaResponse>>()
        )).thenThrow(RuntimeException("Error fetching product areas"))

        val teamCatalog = TeamCatalog(externalServiceWebClient)
        val result = teamCatalog.fetchMembersWithRole()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchMembersWithRole return empty list on incorrect response type`() {
        whenever(responseSpec.onStatus(any(), any())).thenReturn(responseSpec)
        whenever(
            responseSpec.bodyToMono(any<ParameterizedTypeReference<ProductAreaResponse>>()
            )).thenReturn(Mono.empty())

        val teamCatalog = TeamCatalog(externalServiceWebClient)
        val result = teamCatalog.fetchMembersWithRole()
        assertTrue(result.isEmpty())
    }
}