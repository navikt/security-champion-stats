package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.events.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class PostgresEventClaimStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) : EventClaimStore {
    override fun lockScoring() {
        jdbc.queryForObject("SELECT version FROM program_scoring_configuration WHERE singleton = TRUE FOR UPDATE", Long::class.java)
    }

    override fun currentSeason(): Pair<UUID, LocalDate> = requireNotNull(jdbc.queryForObject(
        "SELECT id, starts_on FROM program_seasons WHERE ends_on IS NULL FOR SHARE",
        { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getObject("starts_on", LocalDate::class.java) },
    ))

    override fun list(participantId: UUID?): List<EventClaim> {
        val claims = if (participantId == null) {
            jdbc.query("$claimQuery ORDER BY c.created_at DESC, c.id", claimMapper)
        } else {
            jdbc.query(
                """$claimQuery WHERE submitter_id = ? OR EXISTS
                    (SELECT 1 FROM event_claim_contributors h WHERE h.claim_id = c.id AND h.participant_id = ?)
                    ORDER BY c.created_at DESC, c.id""",
                claimMapper, participantId, participantId,
            )
        }
        return hydrate(claims)
    }

    override fun find(id: UUID, lock: Boolean): EventClaim? =
        hydrate(jdbc.query(
            "$claimQuery WHERE c.id = ?" + if (lock) " FOR UPDATE OF c" else "",
            claimMapper, id,
        )).singleOrNull()

    private val claimQuery = """SELECT c.*, s.starts_on FROM event_contribution_claims c
        JOIN program_seasons s ON s.id = c.season_id"""

    private val claimMapper = RowMapper { rs, _ -> EventClaim(
        rs.getObject("id", UUID::class.java), rs.getObject("submitter_id", UUID::class.java),
        rs.getObject("season_id", UUID::class.java), rs.getObject("starts_on", LocalDate::class.java),
        rs.getLong("version"), rs.getObject("event_id", UUID::class.java), rs.getBoolean("published"),
        rs.getString("name"), rs.getString("description"), rs.getTimestamp("start_date").toInstant(),
        rs.getTimestamp("end_date").toInstant(), rs.getString("location"),
        rs.getString("event_type").lowercase(), rs.getBoolean("external_event"),
        mapper.readValue(rs.getString("links"), Array<String>::class.java).toList(),
        rs.getString("invitation_evidence"), emptyList(), emptyList(),
    ) }

    private fun hydrate(claims: List<EventClaim>): List<EventClaim> {
        if (claims.isEmpty()) return claims
        val ids = claims.map { it.id }.toTypedArray()
        val contributors = jdbc.query(
            """SELECT h.*, p.fullname FROM event_claim_contributors h
                JOIN program_participants p ON p.id = h.participant_id
                WHERE claim_id = ANY (?::uuid[]) ORDER BY claim_id, p.fullname, p.id""",
            { rs, _ -> rs.getObject("claim_id", UUID::class.java) to EventClaimContributor(
                rs.getObject("participant_id", UUID::class.java), rs.getString("fullname"),
                rs.getString("contribution"), ContributionStatus.valueOf(rs.getString("status")),
                rs.getObject("credit_id", UUID::class.java),
            ) }, ids as Any,
        ).groupBy({ it.first }, { it.second })
        val reviews = jdbc.query(
            """SELECT r.*, p.fullname FROM event_claim_reviews r
                JOIN program_participants p ON p.id = r.participant_id
                WHERE claim_id = ANY (?::uuid[]) ORDER BY claim_id, r.created_at, r.id""",
            { rs, _ -> rs.getObject("claim_id", UUID::class.java) to EventClaimReview(
                rs.getObject("participant_id", UUID::class.java), rs.getString("fullname"),
                ContributionStatus.valueOf(rs.getString("decision")),
                rs.getString("reason"), rs.getTimestamp("created_at").toInstant(),
            ) }, ids as Any,
        ).groupBy({ it.first }, { it.second })
        return claims.map {
            it.copy(contributors = contributors[it.id].orEmpty(), reviews = reviews[it.id].orEmpty())
        }
    }

    override fun eligible(participantId: UUID, eventAt: Instant): Boolean {
        // Keep identity and enrollment stable until the claim transaction completes.
        return jdbc.query(
            "SELECT id FROM program_participants WHERE id = ? AND status = 'ACTIVE' AND created_at <= ? FOR UPDATE",
            { rs, _ -> rs.getObject("id", UUID::class.java) }, participantId, Timestamp.from(eventAt),
        ).isNotEmpty()
    }

    override fun validateExistingEvent(request: EventClaimRequest): Boolean =
        jdbc.query(
            """SELECT id FROM Events WHERE id = ? AND lower(btrim(name)) = lower(?)
                AND start_date = ? AND end_date = ? AND lower(btrim(coalesce(location, ''))) = lower(?)
                AND external_event = ? AND event_type = ?""",
            { rs, _ -> rs.getObject("id", UUID::class.java) }, request.eventId, request.name,
            Timestamp.from(request.startDate), Timestamp.from(request.endDate), request.location,
            request.externalEvent, request.type.uppercase(),
        ).isNotEmpty()

    override fun save(id: UUID, submitterId: UUID, seasonId: UUID, request: EventClaimRequest, updating: Boolean) {
        val duplicate = jdbc.query(
            """SELECT id FROM event_contribution_claims WHERE id <> ? AND (
                (lower(btrim(name)) = lower(?) AND start_date = ? AND lower(btrim(location)) = lower(?))
                OR (event_id IS NOT NULL AND event_id = ?)
                OR (start_date = ? AND links ??| ?::text[]))""",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            id, request.name, Timestamp.from(request.startDate), request.location, request.eventId,
            Timestamp.from(request.startDate), request.links.toTypedArray(),
        )
        if (duplicate.isNotEmpty()) throw EventClaimException(
            EventClaimFailure.CONFLICT,
            "An event claim already exists with these event details or links. Ask its submitter to include your contribution",
        )
        if (updating) {
            jdbc.update(
                """UPDATE event_contribution_claims SET name = ?, description = ?, location = ?, event_type = ?,
                    external_event = ?, links = ?::jsonb, invitation_evidence = ?, version = version + 1 WHERE id = ?""",
                request.name, request.description, request.location, request.type.uppercase(), request.externalEvent,
                mapper.writeValueAsString(request.links), request.invitationEvidence, id,
            )
            jdbc.update("DELETE FROM event_claim_contributors WHERE claim_id = ?", id)
        } else {
            jdbc.update(
                """INSERT INTO event_contribution_claims
                    (id, submitter_id, season_id, event_id, name, description, start_date, end_date,
                    location, event_type, external_event, links, invitation_evidence)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)""",
                id, submitterId, seasonId, request.eventId, request.name, request.description,
                Timestamp.from(request.startDate), Timestamp.from(request.endDate), request.location,
                request.type.uppercase(), request.externalEvent, mapper.writeValueAsString(request.links), request.invitationEvidence,
            )
        }
        request.contributors.forEach {
            jdbc.update(
                "INSERT INTO event_claim_contributors (claim_id, participant_id, contribution) VALUES (?, ?, ?)",
                id, it.participantId, it.contribution,
            )
        }
    }

    override fun award(claim: EventClaim, participantId: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO activity_credits
                (id, participant_id, season_id, credit_type, uniqueness_key, source_reference, points, activity_at,
                    source_name, source_url, source_occurred_at)
                SELECT ?, ?, ?, 'SECURITY_EVENT_CONTRIBUTION', ?, ?, points, ?, ?, ?, ?
                FROM program_activity_points WHERE credit_type = 'SECURITY_EVENT_CONTRIBUTION'""",
            id, participantId, claim.seasonId, "event-claim:${claim.id}", "event-claim:${claim.id}", Timestamp.from(claim.startDate),
            claim.name, claim.links.first(), Timestamp.from(claim.startDate),
        )
        return id
    }

    override fun revoke(creditId: UUID): Int {
        val points = requireNotNull(jdbc.queryForObject(
            """SELECT c.points + coalesce((SELECT sum(a.points_delta) FROM point_adjustments a
                WHERE a.source_credit_id = c.id AND a.scoring_configuration_version IS NOT NULL), 0)
                FROM activity_credits c WHERE c.id = ? FOR UPDATE""",
            Long::class.java, creditId,
        ))
        jdbc.update("UPDATE activity_credits SET revoked_at = NOW() WHERE id = ?", creditId)
        return Math.toIntExact(points)
    }

    override fun publish(claim: EventClaim) {
        val eventId = claim.eventId ?: jdbc.query(
            """SELECT id FROM Events WHERE lower(btrim(name)) = lower(?) AND start_date = ?
                AND lower(btrim(coalesce(location, ''))) = lower(?)""",
            { rs, _ -> rs.getObject("id", UUID::class.java) }, claim.name, Timestamp.from(claim.startDate), claim.location,
        ).singleOrNull() ?: UUID.randomUUID().also {
            jdbc.update(
                """INSERT INTO Events (id, name, description, start_date, end_date, external_event,
                    delta_event, location, event_type, amount_of_people_joined, link)
                    VALUES (?, ?, ?, ?, ?, ?, FALSE, ?, ?, 0, ?)""",
                it, claim.name, claim.description, Timestamp.from(claim.startDate), Timestamp.from(claim.endDate),
                claim.externalEvent, claim.location, claim.type.uppercase(), claim.links.first(),
            )
        }
        jdbc.update("UPDATE event_contribution_claims SET event_id = ?, published = TRUE WHERE id = ?", eventId, claim.id)
    }

    override fun review(claimId: UUID, request: EventClaimReviewRequest, actorEmail: String, creditId: UUID?) {
        jdbc.update(
            "UPDATE event_claim_contributors SET status = ?, credit_id = ? WHERE claim_id = ? AND participant_id = ?",
            request.decision.name, creditId, claimId, request.participantId,
        )
        jdbc.update(
            """INSERT INTO event_claim_reviews
                (claim_id, participant_id, actor_id, actor_nav_no_email, decision, reason)
                VALUES (?, ?, (SELECT id FROM program_participants WHERE nav_no_email = ?), ?, ?, ?)""",
            claimId, request.participantId, actorEmail, actorEmail, request.decision.name, request.reason,
        )
        jdbc.update("UPDATE event_contribution_claims SET version = version + 1 WHERE id = ?", claimId)
    }
}
