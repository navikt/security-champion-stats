package navikt.appsec.securitychampionapp.integrations.playbook

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.codec.DecodingException
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.bodyToMono
import java.net.URI
import java.time.Duration
import java.time.LocalDate

@Component
class PlaybookEventClient(
    @Value($$"${playbook.events.feed-url:https://sikkerhet.nav.no/events.json}") private val feedUrl: String,
) {
    private val client = WebClient.builder().build()

    fun fetchEvents(): List<PlaybookEvent> {
        val feed = try {
            client.get().uri(feedUrl).retrieve().bodyToMono<PlaybookFeed>()
                .block(Duration.ofSeconds(20))
                ?: throw IllegalStateException("Playbook returned an empty response")
        } catch (e: WebClientException) {
            throw IllegalStateException("Playbook event feed request failed", e)
        } catch (e: DecodingException) {
            throw IllegalStateException("Playbook event feed could not be decoded", e)
        }
        require(feed.schemaVersion == 1) { "Unsupported playbook event feed schema version" }
        require(feed.events.map { it.id }.distinct().size == feed.events.size) {
            "Playbook event feed contains duplicate IDs"
        }
        feed.events.forEach { event ->
            require(
                (event.id.startsWith("playbook:") || event.id.startsWith("external:")) &&
                    event.id.substringAfter(':').isNotBlank()
            ) { "Playbook event has an invalid source ID" }
            require(event.title.isNotBlank() && event.audience.isNotBlank()) {
                "Playbook event ${event.id} is missing title or audience"
            }
            require(event.startDate.matches(DATE_PATTERN) && event.endDate.matches(DATE_PATTERN)) {
                "Playbook event ${event.id} must have date-only ISO dates"
            }
            val start = LocalDate.parse(event.startDate)
            val end = LocalDate.parse(event.endDate)
            require(!end.isBefore(start)) { "Playbook event ${event.id} has a reversed date range" }
            val url = URI(event.url)
            require(url.scheme in setOf("http", "https") && !url.host.isNullOrBlank() && url.userInfo == null) {
                "Playbook event ${event.id} has an invalid URL"
            }
        }
        return feed.events
    }

    private companion object {
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

private data class PlaybookFeed(val schemaVersion: Int, val events: List<PlaybookEvent>)
