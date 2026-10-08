package navikt.appsec.securitychampionapp.api

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import navikt.appsec.securitychampionapp.app.api.AdminEventReminderController
import navikt.appsec.securitychampionapp.app.events.*
import navikt.appsec.securitychampionapp.app.jobs.EventReminderJob
import navikt.appsec.securitychampionapp.app.jobs.SyncTriggerResult
import navikt.appsec.securitychampionapp.config.SecurityConfig
import navikt.appsec.securitychampionapp.security.AppAuthenticationFilter
import navikt.appsec.securitychampionapp.security.dto.AppPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@WebMvcTest(AdminEventReminderController::class)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AdminEventReminderControllerTest {
    @Autowired
    lateinit var mvc: MockMvc
    @MockitoBean
    lateinit var service: EventReminderService
    @MockitoBean
    lateinit var job: EventReminderJob
    @MockitoBean
    lateinit var filter: AppAuthenticationFilter
    private val path = "/api/admin/events/synthetic-event/reminders"

    @Test
    fun `non admins cannot inspect recipients or send reminders`() {
        authenticated("USER")
        mvc.perform(get(path)).andExpect(status().isForbidden)
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":"reviewed","message":"Edited reminder","confirmed":true}""")).andExpect(status().isForbidden)
        verifyNoInteractions(service, job)
    }

    @Test
    fun `admin previews read only recipients with no shared caching`() {
        authenticated("ADMIN")
        whenever(service.preview("synthetic-event")).thenReturn(
            EventReminderPreview("reviewed", Instant.parse("2026-10-08T08:00:00Z"), "Synthetic reminder", 2, emptyList()),
        )
        mvc.perform(get(path)).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.version").value("reviewed"))
        verifyNoInteractions(job)
    }

    @Test
    fun `confirmation is required and accepted requests forward the reviewed version and administrator`() {
        authenticated("ADMIN")
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":"reviewed","message":"Edited reminder","confirmed":false}""")).andExpect(status().isBadRequest)
        verifyNoInteractions(job)
        whenever(job.send("synthetic-event", "reviewed", "Edited reminder", "admin@nav.no")).thenReturn(SyncTriggerResult.STARTED)
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":"reviewed","message":"Edited reminder","confirmed":true}""")).andExpect(status().isAccepted)
        verify(job).send("synthetic-event", "reviewed", "Edited reminder", "admin@nav.no")
    }

    @Test
    fun `Delta unavailable and stale previews return sanitized problem details`() {
        authenticated("ADMIN")
        whenever(service.preview(any())).thenThrow(EventSignupUnavailableException())
        mvc.perform(get(path)).andExpect(status().isServiceUnavailable)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Delta signup information could not be verified"))
        whenever(job.send(any(), any(), any(), any())).thenThrow(EventReminderConflictException("Preview again"))
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":"reviewed","message":"Edited reminder","confirmed":true}""")).andExpect(status().isConflict)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `busy and unavailable queues are not reported as accepted`() {
        authenticated("ADMIN")
        whenever(job.send(any(), any(), any(), any())).thenReturn(SyncTriggerResult.ALREADY_RUNNING, SyncTriggerResult.UNAVAILABLE)
        val request = { post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":"reviewed","message":"Edited reminder","confirmed":true}""") }
        mvc.perform(request()).andExpect(status().isConflict)
        mvc.perform(request()).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `missing or invalid messages return problem details rather than acceptance`() {
        authenticated("ADMIN")
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":"reviewed","confirmed":true}""")).andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        verifyNoInteractions(job)
        whenever(job.send(any(), any(), any(), any())).thenThrow(InvalidEventReminderMessageException())
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":"reviewed","message":" ","confirmed":true}""")).andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid reminder message"))
    }

    private fun authenticated(role: String) {
        Mockito.doAnswer { invocation ->
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AppPrincipal("admin@nav.no", "A12345"), null, listOf(SimpleGrantedAuthority("ROLE_$role")),
            )
            try {
                invocation.getArgument<FilterChain>(2).doFilter(
                    invocation.getArgument<ServletRequest>(0), invocation.getArgument<ServletResponse>(1),
                )
            } finally {
                SecurityContextHolder.clearContext()
            }
            null
        }.`when`(filter).doFilter(any(), any(), any())
    }
}
