package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.scoring.ActivityCredit
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.CreditSourceContext
import navikt.appsec.securitychampionapp.app.scoring.ScoringHistoryEntry
import navikt.appsec.securitychampionapp.app.scoring.ScoringHistoryEntryType
import navikt.appsec.securitychampionapp.app.scoring.ScoreHistorySeason
import navikt.appsec.securitychampionapp.app.scoring.ScoreHistoryRecord
import navikt.appsec.securitychampionapp.app.scoring.ScoreHistoryCursor
import navikt.appsec.securitychampionapp.app.scoring.CreditAwardResult
import navikt.appsec.securitychampionapp.app.scoring.ParticipantSeasonScore
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustment
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.ScoringTargetNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.SourceCreditNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.ScoringLedger
import navikt.appsec.securitychampionapp.app.scoring.ActivityPoints
import navikt.appsec.securitychampionapp.app.scoring.ScoringConfiguration
import navikt.appsec.securitychampionapp.app.scoring.ScoringConfigurationPreview
import navikt.appsec.securitychampionapp.app.scoring.ScoringConfigurationRequest
import navikt.appsec.securitychampionapp.app.scoring.ScoringImpact
import navikt.appsec.securitychampionapp.app.scoring.ScoringTier
import navikt.appsec.securitychampionapp.app.scoring.StaleScoringConfigurationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

@Repository
class PostgresScoringLedger(
    private val jdbcTemplate: JdbcTemplate,
) : ScoringLedger {
    @Transactional
    override fun configuration(): ScoringConfiguration {
        val version = lockConfiguration()
        val tiers = jdbcTemplate.query(
            "SELECT name, points FROM program_scoring_tiers ORDER BY points",
            { rs, _ -> ScoringTier(rs.getString("name"), rs.getInt("points")) },
        )
        val activities = jdbcTemplate.query(
            "SELECT credit_type, points FROM program_activity_points",
            { rs, _ -> ActivityPoints(ActivityCreditType.valueOf(rs.getString("credit_type")), rs.getInt("points")) },
        ).sortedBy { it.creditType.ordinal }
        check(tiers.isNotEmpty() && tiers.first().points == 0) { "Scoring tiers are incomplete" }
        check(activities.map { it.creditType }.toSet() == ActivityCreditType.entries.toSet()) {
            "Activity scoring configuration is incomplete"
        }
        return ScoringConfiguration(version, tiers, activities)
    }

    private fun lockConfiguration(forUpdate: Boolean = false): Long = jdbcTemplate.queryForObject(
        if (forUpdate) {
            "SELECT version FROM program_scoring_configuration WHERE singleton = TRUE FOR UPDATE"
        } else {
            "SELECT version FROM program_scoring_configuration WHERE singleton = TRUE FOR SHARE"
        },
        Long::class.javaObjectType,
    ) ?: error("No scoring configuration exists")

    @Transactional
    override fun previewConfiguration(request: ScoringConfigurationRequest): ScoringConfigurationPreview {
        val configuration = configuration()
        if (configuration.version != request.expectedVersion) throw StaleScoringConfigurationException()
        return configurationChanges(request, configuration).preview
    }

    @Transactional
    override fun saveConfiguration(request: ScoringConfigurationRequest, actor: String): ScoringConfiguration {
        if (lockConfiguration(forUpdate = true) != request.expectedVersion) throw StaleScoringConfigurationException()
        val configuration = configuration()
        // Awards share the configuration lock; corrections/deletions share participant locks.
        // Lock the season before computing the preview so a reset cannot move the affected credits.
        jdbcTemplate.queryForObject(
            "SELECT id FROM program_seasons WHERE ends_on IS NULL FOR SHARE", UUID::class.java,
        )
        jdbcTemplate.query(
            "SELECT id FROM program_participants ORDER BY id FOR UPDATE",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
        )
        val changes = configurationChanges(request, configuration)
        if (changes.preview.token != request.previewToken) throw StaleScoringConfigurationException()
        val beforeValues = configurationAuditValues()
        val newVersion = configuration.version + 1
        jdbcTemplate.update("DELETE FROM program_scoring_tiers")
        request.tiers.forEach {
            jdbcTemplate.update("INSERT INTO program_scoring_tiers (name, points) VALUES (?, ?)", it.name, it.points)
        }
        request.activities.forEach {
            jdbcTemplate.update(
                "UPDATE program_activity_points SET points = ? WHERE credit_type = ?", it.points, it.creditType.name,
            )
        }
        jdbcTemplate.update("UPDATE program_scoring_configuration SET version = ? WHERE singleton = TRUE", newVersion)
        changes.credits.forEach { credit ->
            val adjustment = addAdjustment(
                credit.participantId,
                credit.delta,
                "Scoring rule update: ${request.reason}",
                actor,
                credit.id,
            )
            jdbcTemplate.update(
                "UPDATE point_adjustments SET scoring_configuration_version = ? WHERE id = ?", newVersion, adjustment.id,
            )
        }
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    actor_nav_no_email, action, reason, before_values, after_values
                ) VALUES (
                    ?, 'SCORING_CONFIGURATION_UPDATED', ?,
                    ?::jsonb,
                    ?::jsonb || jsonb_build_object('retroactive', ?, 'affectedCredits', ?, 'pointsDelta', ?)
                )
            """.trimIndent(),
            actor, request.reason, beforeValues, configurationAuditValues(), request.applyRetroactively,
            changes.preview.affectedCredits, changes.preview.pointsDelta,
        )
        return configuration()
    }

    private fun configurationAuditValues(): String = jdbcTemplate.queryForObject(
        """
            SELECT jsonb_build_object(
                'version', version,
                'tiers', (SELECT jsonb_agg(jsonb_build_object('name', name, 'points', points) ORDER BY points)
                    FROM program_scoring_tiers),
                'activities', (SELECT jsonb_agg(jsonb_build_object('creditType', credit_type, 'points', points)
                    ORDER BY credit_type) FROM program_activity_points)
            )::text
            FROM program_scoring_configuration WHERE singleton = TRUE
        """.trimIndent(),
        String::class.java,
    ) ?: error("No scoring configuration exists")

    private data class CreditChange(val id: UUID, val participantId: UUID, val delta: Int)
    private data class ConfigurationChanges(
        val credits: List<CreditChange>,
        val preview: ScoringConfigurationPreview,
    )

    private fun configurationChanges(
        request: ScoringConfigurationRequest,
        before: ScoringConfiguration,
    ): ConfigurationChanges {
        jdbcTemplate.queryForObject(
            "SELECT id FROM program_seasons WHERE ends_on IS NULL FOR SHARE", UUID::class.java,
        )
        val season = currentSeason()
        val desiredPoints = request.activities.associate { it.creditType to it.points }
        val credits = if (request.applyRetroactively) {
            jdbcTemplate.query(
                """
                    SELECT credit.id, credit.participant_id, credit.credit_type,
                        credit.points + COALESCE(SUM(adjustment.points_delta), 0) AS effective_points
                    FROM activity_credits AS credit
                    LEFT JOIN point_adjustments AS adjustment ON adjustment.source_credit_id = credit.id
                        AND adjustment.scoring_configuration_version IS NOT NULL
                    WHERE credit.season_id = ? AND credit.revoked_at IS NULL
                    GROUP BY credit.id
                    ORDER BY credit.id
                """.trimIndent(),
                { rs, _ ->
                    CreditChange(
                        rs.getObject("id", UUID::class.java),
                        rs.getObject("participant_id", UUID::class.java),
                        Math.toIntExact(
                            desiredPoints.getValue(ActivityCreditType.valueOf(rs.getString("credit_type"))).toLong() -
                                rs.getLong("effective_points"),
                        ),
                    )
                },
                season.id,
            ).filter { it.delta != 0 }
        } else {
            emptyList()
        }
        val deltas = credits.groupBy { it.participantId }.mapValues { (_, values) -> values.sumOf { it.delta.toLong() } }
        val after = ScoringConfiguration(before.version + 1, request.tiers, request.activities)
        val scores = scoresForSeason(season.id).sortedBy { it.participantId }
        val impacts = scores.map {
            val pointsAfter = it.points + (deltas[it.participantId] ?: 0L)
            ScoringImpact(
                it.participantId, it.fullName, it.points, pointsAfter,
                before.levelFor(it.points), after.levelFor(pointsAfter),
            )
        }.filter { it.pointsBefore != it.pointsAfter || it.levelBefore != it.levelAfter }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeLong(before.version)
            out.writeUTF(season.id.toString())
            out.writeUTF(season.startsOn.toString())
            out.writeUTF(season.nextResetDate.toString())
            out.writeBoolean(request.applyRetroactively)
            out.writeUTF(request.reason)
            out.writeInt(request.tiers.size)
            request.tiers.forEach { out.writeUTF(it.name); out.writeInt(it.points) }
            request.activities.forEach { out.writeUTF(it.creditType.name); out.writeInt(it.points) }
            out.writeInt(credits.size)
            credits.forEach {
                out.writeUTF(it.id.toString()); out.writeUTF(it.participantId.toString()); out.writeInt(it.delta)
            }
            out.writeInt(scores.size)
            scores.forEach {
                out.writeUTF(it.participantId.toString()); out.writeUTF(it.fullName); out.writeLong(it.points)
            }
        }
        val token = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
        return ConfigurationChanges(
            credits,
            ScoringConfigurationPreview(token, season, credits.size, credits.sumOf { it.delta.toLong() }, impacts),
        )
    }

    override fun creditPoints(participantId: UUID, creditType: ActivityCreditType, uniquenessKey: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT points FROM activity_credits WHERE participant_id = ? AND credit_type = ? AND uniqueness_key = ?",
            Int::class.javaObjectType, participantId, creditType.name, uniquenessKey,
        ) ?: error("The awarded credit does not exist")

    private val seasonMapper = RowMapper { rs, _ ->
        SeasonSummary(
            id = rs.getObject("id", UUID::class.java),
            startsOn = rs.getObject("starts_on", LocalDate::class.java),
            endsOn = rs.getObject("ends_on", LocalDate::class.java),
            nextResetDate = rs.getObject("next_reset_date", LocalDate::class.java),
        )
    }

    private val scoreMapper = RowMapper { rs, _ ->
        ParticipantSeasonScore(
            participantId = rs.getObject("participant_id", UUID::class.java),
            fullName = rs.getString("fullname"),
            email = rs.getString("email"),
            active = rs.getBoolean("active"),
            points = rs.getLong("points"),
        )
    }

    override fun currentSeason(): SeasonSummary =
        jdbcTemplate.queryForObject(
            """
                SELECT season.id, season.starts_on, season.ends_on, settings.next_reset_date
                FROM program_seasons AS season
                CROSS JOIN program_season_settings AS settings
                WHERE season.ends_on IS NULL AND settings.singleton = TRUE
            """.trimIndent(),
            seasonMapper,
        )

    override fun scoreHistorySeasons(): List<ScoreHistorySeason> =
        jdbcTemplate.query(
            """
                SELECT id, starts_on, ends_on
                FROM program_seasons
                ORDER BY starts_on DESC
            """.trimIndent(),
            { rs, _ ->
                ScoreHistorySeason(
                    id = rs.getObject("id", UUID::class.java),
                    startsOn = rs.getObject("starts_on", LocalDate::class.java),
                    endsOn = rs.getObject("ends_on", LocalDate::class.java),
                )
            },
        )

    override fun scoresForCurrentSeason(activeOnly: Boolean): List<ParticipantSeasonScore> =
        scoresForSeason(currentSeason().id, activeOnly)

    override fun scoresForSeason(
        seasonId: UUID,
        activeOnly: Boolean,
    ): List<ParticipantSeasonScore> =
        jdbcTemplate.query(
            scoreQuery,
            scoreMapper,
            seasonId,
            seasonId,
            activeOnly,
        )

    override fun scoreForParticipant(participantId: UUID, seasonId: UUID): Long =
        jdbcTemplate.queryForObject(
            """
                SELECT
                    COALESCE((SELECT SUM(points) FROM activity_credits
                        WHERE participant_id = ? AND season_id = ?), 0)
                    + COALESCE((SELECT SUM(points_delta) FROM point_adjustments
                        WHERE participant_id = ? AND season_id = ?), 0)
            """.trimIndent(),
            Long::class.javaObjectType,
            participantId,
            seasonId,
            participantId,
            seasonId,
        ) ?: 0L

    override fun creditsForParticipant(participantId: UUID): List<ActivityCredit> =
        jdbcTemplate.query(
            """
                SELECT credit.id, credit.credit_type, credit.source_reference, credit.points, season.starts_on
                FROM activity_credits AS credit
                JOIN program_seasons AS season ON season.id = credit.season_id
                WHERE credit.participant_id = ?
                ORDER BY credit.awarded_at DESC, credit.id
            """.trimIndent(),
            { rs, _ ->
                ActivityCredit(
                    id = rs.getObject("id", UUID::class.java),
                    creditType = ActivityCreditType.valueOf(rs.getString("credit_type")),
                    sourceReference = rs.getString("source_reference"),
                    points = rs.getInt("points"),
                    seasonStartsOn = rs.getObject("starts_on", LocalDate::class.java),
                )
            },
            participantId,
        )

    override fun scoringHistoryForParticipant(participantId: UUID): List<ScoringHistoryEntry> =
        jdbcTemplate.query(
            """
                SELECT credit.id, 'CREDIT' AS type, credit.awarded_at AS recorded_at, credit.activity_at,
                    credit.season_id, season.starts_on, season.ends_on, credit.points,
                    credit.credit_type, credit.source_reference, NULL::uuid AS source_credit_id,
                    NULL::text AS reason, NULL::text AS actor_nav_no_email, credit.revoked_at,
                    credit.source_name, credit.source_url, credit.source_occurred_at
                FROM activity_credits AS credit
                JOIN program_seasons AS season ON season.id = credit.season_id
                WHERE credit.participant_id = ?
                UNION ALL
                SELECT adjustment.id,
                    CASE WHEN adjustment.scoring_configuration_version IS NULL
                        THEN 'ADJUSTMENT' ELSE 'SCORING_RULE_CHANGE' END,
                    adjustment.created_at, NULL::timestamptz,
                    adjustment.season_id, season.starts_on, season.ends_on, adjustment.points_delta,
                    credit.credit_type, credit.source_reference, adjustment.source_credit_id,
                    adjustment.reason, adjustment.actor_nav_no_email, NULL::timestamptz,
                    credit.source_name, credit.source_url, credit.source_occurred_at
                FROM point_adjustments AS adjustment
                JOIN program_seasons AS season ON season.id = adjustment.season_id
                LEFT JOIN activity_credits AS credit ON credit.id = adjustment.source_credit_id
                WHERE adjustment.participant_id = ?
                ORDER BY recorded_at DESC, id DESC
            """.trimIndent(),
            { rs, _ ->
                ScoringHistoryEntry(
                    id = rs.getObject("id", UUID::class.java),
                    type = ScoringHistoryEntryType.valueOf(rs.getString("type")),
                    recordedAt = rs.getTimestamp("recorded_at").toInstant(),
                    activityAt = rs.getTimestamp("activity_at")?.toInstant(),
                    seasonId = rs.getObject("season_id", UUID::class.java),
                    seasonStartsOn = rs.getObject("starts_on", LocalDate::class.java),
                    seasonEndsOn = rs.getObject("ends_on", LocalDate::class.java),
                    points = rs.getInt("points"),
                    creditType = rs.getString("credit_type")?.let(ActivityCreditType::valueOf),
                    sourceReference = rs.getString("source_reference"),
                    sourceCreditId = rs.getObject("source_credit_id", UUID::class.java),
                    reason = rs.getString("reason"),
                    actorNavNoEmail = rs.getString("actor_nav_no_email"),
                    revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
                    displayName = rs.getString("source_name"),
                    sourceUrl = rs.getString("source_url"),
                    sourceOccurredAt = rs.getTimestamp("source_occurred_at")?.toInstant(),
                )
            },
            participantId,
            participantId,
        )

    override fun scoreHistoryPage(
        participantId: UUID,
        seasonId: UUID?,
        type: String,
        cursor: ScoreHistoryCursor?,
        limit: Int,
    ): List<ScoreHistoryRecord> =
        jdbcTemplate.query(
            """
                WITH history AS (
                    SELECT credit.id::text AS id, 'CREDIT' AS type, credit.awarded_at AS recorded_at,
                        credit.activity_at, credit.season_id, credit.credit_type, credit.points,
                        credit.source_name AS display_name, credit.source_reference,
                        credit.id::text AS credit_id, NULL::text AS linked_credit_id,
                        NULL::text AS reason, NULL::text AS admin_name, credit.revoked_at,
                        NULL::text AS membership_action, NULL::text AS membership_status_before,
                        NULL::text AS membership_status_after, NULL::text AS membership_reason,
                        credit.source_url, credit.source_occurred_at
                    FROM activity_credits AS credit
                    WHERE credit.participant_id = ?
                    UNION ALL
                    SELECT adjustment.id::text,
                        CASE WHEN adjustment.scoring_configuration_version IS NULL
                            THEN 'ADJUSTMENT' ELSE 'SCORING_RULE_CHANGE' END,
                        adjustment.created_at, NULL::timestamptz, adjustment.season_id,
                        credit.credit_type, adjustment.points_delta, credit.source_name,
                        credit.source_reference, NULL::text, adjustment.source_credit_id::text,
                        adjustment.reason, adjustment.actor_nav_no_email, NULL::timestamptz,
                        NULL::text, NULL::text, NULL::text, NULL::text,
                        credit.source_url, credit.source_occurred_at
                    FROM point_adjustments AS adjustment
                    LEFT JOIN activity_credits AS credit ON credit.id = adjustment.source_credit_id
                    WHERE adjustment.participant_id = ?
                    UNION ALL
                    SELECT event.id::text, 'MEMBERSHIP', event.created_at,
                        NULL::timestamptz, membership_season.id, NULL::text, NULL::integer,
                        NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text,
                        NULL::timestamptz,
                        CASE event.action
                            WHEN 'PARTICIPANT_ENROLLED' THEN 'joined'
                            WHEN 'PARTICIPANT_REJOINED' THEN 'rejoined'
                            ELSE 'left'
                        END,
                        NULL::text, NULL::text, NULL::text, NULL::text, NULL::timestamptz
                    FROM program_audit_events AS event
                    LEFT JOIN LATERAL (
                        SELECT season.id
                        FROM program_seasons AS season
                        WHERE (event.created_at AT TIME ZONE 'Europe/Oslo')::date >= season.starts_on
                            AND (
                                season.ends_on IS NULL
                                OR (event.created_at AT TIME ZONE 'Europe/Oslo')::date <= season.ends_on
                            )
                        ORDER BY season.starts_on DESC
                        LIMIT 1
                    ) AS membership_season ON TRUE
                    WHERE event.target_participant_id = ?
                        AND event.outcome = 'SUCCEEDED'
                        AND event.action IN ('PARTICIPANT_ENROLLED', 'PARTICIPANT_LEFT', 'PARTICIPANT_REJOINED')
                    UNION ALL
                    SELECT ('membership-status:' || event.id)::text, 'MEMBERSHIP', event.created_at,
                        NULL::timestamptz, membership_season.id, NULL::text, NULL::integer,
                        NULL::text, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text,
                        NULL::timestamptz, 'status_changed',
                        event.before_values ->> 'status', event.after_values ->> 'status',
                        event.after_values ->> 'reason', NULL::text, NULL::timestamptz
                    FROM program_participant_audit AS event
                    LEFT JOIN LATERAL (
                        SELECT season.id
                        FROM program_seasons AS season
                        WHERE (event.created_at AT TIME ZONE 'Europe/Oslo')::date >= season.starts_on
                            AND (
                                season.ends_on IS NULL
                                OR (event.created_at AT TIME ZONE 'Europe/Oslo')::date <= season.ends_on
                            )
                        ORDER BY season.starts_on DESC
                        LIMIT 1
                    ) AS membership_season ON TRUE
                    WHERE event.participant_id = ?
                        AND event.action = 'PARTICIPATION_STATUS_CHANGED'
                        AND event.created_at >= (
                            SELECT started_at FROM program_audit_rollout WHERE singleton = TRUE
                        )
                ),
                numbered AS (
                    SELECT history.*,
                        ROW_NUMBER() OVER (PARTITION BY recorded_at ORDER BY id DESC) AS tie_index
                    FROM history
                )
                SELECT *
                FROM numbered
                WHERE (
                    CAST(? AS text) = 'all'
                    OR (CAST(? AS text) = 'credit' AND type = 'CREDIT')
                    OR (CAST(? AS text) = 'adjustment' AND type IN ('ADJUSTMENT', 'SCORING_RULE_CHANGE'))
                    OR (CAST(? AS text) = 'membership' AND type = 'MEMBERSHIP')
                )
                    AND (CAST(? AS uuid) IS NULL OR season_id = CAST(? AS uuid))
                    AND (
                        CAST(? AS timestamptz) IS NULL
                        OR recorded_at < CAST(? AS timestamptz)
                        OR (recorded_at = CAST(? AS timestamptz) AND tie_index > ?)
                    )
                ORDER BY recorded_at DESC, tie_index ASC
                LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                ScoreHistoryRecord(
                    id = rs.getString("id"),
                    type = rs.getString("type"),
                    recordedAt = rs.getTimestamp("recorded_at").toInstant(),
                    activityAt = rs.getTimestamp("activity_at")?.toInstant(),
                    seasonId = rs.getObject("season_id", UUID::class.java),
                    creditType = rs.getString("credit_type")?.let(ActivityCreditType::valueOf),
                    points = rs.getObject("points", Int::class.javaObjectType),
                    displayName = rs.getString("display_name"),
                    sourceReference = rs.getString("source_reference"),
                    creditId = rs.getString("credit_id"),
                    linkedCreditId = rs.getString("linked_credit_id"),
                    reason = rs.getString("reason"),
                    adminName = rs.getString("admin_name"),
                    revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
                    membershipAction = rs.getString("membership_action"),
                    membershipStatusBefore = rs.getString("membership_status_before"),
                    membershipStatusAfter = rs.getString("membership_status_after"),
                    membershipReason = rs.getString("membership_reason"),
                    tieIndex = rs.getLong("tie_index"),
                    sourceUrl = rs.getString("source_url"),
                    sourceOccurredAt = rs.getTimestamp("source_occurred_at")?.toInstant(),
                )
            },
            participantId,
            participantId,
            participantId,
            participantId,
            type,
            type,
            type,
            type,
            seasonId,
            seasonId,
            cursor?.recordedAt?.let { Timestamp.from(it) },
            cursor?.recordedAt?.let { Timestamp.from(it) },
            cursor?.recordedAt?.let { Timestamp.from(it) },
            cursor?.tieIndex ?: 0L,
            limit,
        )

    override fun participantExists(participantId: UUID): Boolean =
        jdbcTemplate.query(
            "SELECT id FROM program_participants WHERE id = ?",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            participantId,
        ).isNotEmpty()

    override fun updateCreditSource(
        creditType: ActivityCreditType,
        sourceReference: String,
        source: CreditSourceContext,
    ) {
        jdbcTemplate.update(
            """
                UPDATE activity_credits
                SET source_name = COALESCE(?, source_name), source_url = COALESCE(?, source_url),
                    source_occurred_at = COALESCE(?::timestamptz, source_occurred_at)
                WHERE credit_type = ? AND source_reference = ?
                    AND (
                        source_name IS DISTINCT FROM COALESCE(?, source_name)
                        OR source_url IS DISTINCT FROM COALESCE(?, source_url)
                        OR source_occurred_at IS DISTINCT FROM COALESCE(?::timestamptz, source_occurred_at)
                    )
            """.trimIndent(),
            source.name, source.url, source.occurredAt?.let(Timestamp::from), creditType.name, sourceReference,
            source.name, source.url, source.occurredAt?.let(Timestamp::from),
        )
    }

    @Transactional
    override fun awardCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID?,
    ): CreditAwardResult = insertCredit(
        participantId, creditType, uniquenessKey, sourceReference, auditCorrelationId, null, null,
    )

    @Transactional
    override fun awardGitHubCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID?,
        activityAt: Instant,
        expectedSeasonId: UUID,
    ): CreditAwardResult = insertCredit(
        participantId, creditType, uniquenessKey, sourceReference, auditCorrelationId, activityAt, expectedSeasonId,
    )

    private fun insertCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
        auditCorrelationId: UUID?,
        activityAt: Instant?,
        expectedSeasonId: UUID?,
    ): CreditAwardResult {
        lockConfiguration()
        val points = jdbcTemplate.queryForObject(
            "SELECT points FROM program_activity_points WHERE credit_type = ?",
            Int::class.javaObjectType, creditType.name,
        ) ?: error("The activity has no scoring configuration")
        val seasonId = jdbcTemplate.queryForObject(
            "SELECT id FROM program_seasons WHERE ends_on IS NULL FOR SHARE",
            UUID::class.java,
        ) ?: error("No current program season exists")
        check(expectedSeasonId == null || expectedSeasonId == seasonId) { "The scoring season changed during sync" }
        val inserted = jdbcTemplate.update(
            """
                INSERT INTO activity_credits (
                    id, participant_id, season_id, credit_type, uniqueness_key, source_reference, points,
                    audit_correlation_id, activity_at
                )
                SELECT ?, participant.id, ?, ?, ?, ?, ?, ?, ?
                FROM program_participants AS participant
                WHERE participant.id = ? AND participant.status = 'ACTIVE'
                    AND (?::timestamptz IS NULL OR participant.created_at < ?::timestamptz)
                ON CONFLICT DO NOTHING
            """.trimIndent(),
            UUID.randomUUID(),
            seasonId,
            creditType.name,
            uniquenessKey,
            sourceReference,
            points,
            auditCorrelationId,
            activityAt?.let(Timestamp::from),
            participantId,
            activityAt?.let(Timestamp::from),
            activityAt?.let(Timestamp::from),
        )
        if (inserted == 1) return CreditAwardResult.AWARDED

        val duplicate = jdbcTemplate.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1 FROM activity_credits
                    WHERE (participant_id = ? OR credit_type IN ('GITHUB_COMMIT', 'GITHUB_PULL_REQUEST'))
                        AND credit_type = ? AND uniqueness_key = ?
                )
            """.trimIndent(),
            Boolean::class.javaObjectType,
            participantId,
            creditType.name,
            uniquenessKey,
        ) ?: false
        return if (duplicate) {
            CreditAwardResult.DUPLICATE
        } else {
            CreditAwardResult.PARTICIPANT_INACTIVE_OR_MISSING
        }
    }

    @Transactional
    override fun addAdjustment(
        participantId: UUID,
        pointsDelta: Int,
        reason: String,
        actorNavNoEmail: String,
        sourceCreditId: UUID?,
    ): PointAdjustment {
        val participantExists = jdbcTemplate.query(
            "SELECT id FROM program_participants WHERE id = ? FOR UPDATE",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            participantId,
        ).isNotEmpty()
        if (!participantExists) throw ScoringTargetNotFoundException()

        val seasonId = if (sourceCreditId == null) {
            jdbcTemplate.queryForObject(
                "SELECT id FROM program_seasons WHERE ends_on IS NULL FOR SHARE",
                UUID::class.java,
            ) ?: error("No current program season exists")
        } else {
            jdbcTemplate.query(
                """
                    SELECT season_id FROM activity_credits
                    WHERE id = ? AND participant_id = ?
                """.trimIndent(),
                { rs, _ -> rs.getObject("season_id", UUID::class.java) },
                sourceCreditId,
                participantId,
            ).firstOrNull() ?: throw SourceCreditNotFoundException()
        }

        val before = scoreForParticipant(participantId, seasonId)
        val after = before + pointsDelta
        val adjustmentId = UUID.randomUUID()
        jdbcTemplate.update(
            """
                INSERT INTO point_adjustments (
                    id, participant_id, season_id, source_credit_id, points_delta, reason,
                    actor_nav_no_email, score_before, score_after
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            adjustmentId,
            participantId,
            seasonId,
            sourceCreditId,
            pointsDelta,
            reason,
            actorNavNoEmail,
            before,
            after,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    participant_id, actor_nav_no_email, action, affected_record_id, reason,
                    before_values, after_values
                ) VALUES (
                    ?, ?, 'POINTS_ADJUSTED', ?, ?,
                    jsonb_build_object('points', ?),
                    jsonb_build_object('points', ?, 'pointsDelta', ?, 'seasonId', ?)
                )
            """.trimIndent(),
            participantId,
            actorNavNoEmail,
            adjustmentId,
            reason,
            before,
            after,
            pointsDelta,
            seasonId,
        )
        return PointAdjustment(adjustmentId, participantId, seasonId, pointsDelta, before, after)
    }

    @Transactional
    override fun updateNextResetDate(
        newDate: LocalDate,
        actorNavNoEmail: String,
    ): SeasonSummary {
        val current = jdbcTemplate.queryForObject(
            """
                SELECT season.id, season.starts_on, settings.next_reset_date
                FROM program_seasons AS season
                CROSS JOIN program_season_settings AS settings
                WHERE season.ends_on IS NULL AND settings.singleton = TRUE
                FOR UPDATE OF season, settings
            """.trimIndent(),
            { rs, _ ->
                Triple(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("starts_on", LocalDate::class.java),
                    rs.getObject("next_reset_date", LocalDate::class.java),
                )
            },
        )

        jdbcTemplate.update(
            """
                UPDATE program_season_settings
                SET next_reset_date = ?, updated_at = NOW()
                WHERE singleton = TRUE
            """.trimIndent(),
            newDate,
        )
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    actor_nav_no_email, action, affected_record_id, before_values, after_values
                ) VALUES (
                    ?, 'SEASON_RESET_DATE_UPDATED', ?,
                    jsonb_build_object('nextResetDate', ?),
                    jsonb_build_object('nextResetDate', ?)
                )
            """.trimIndent(),
            actorNavNoEmail,
            current.first,
            current.third,
            newDate,
        )
        return currentSeason()
    }

    @Transactional
    override fun resetManually(
        startDate: LocalDate,
        reason: String,
        actorNavNoEmail: String,
    ): SeasonSummary {
        val nextResetDate = lockSettings()
        val current = lockCurrentSeason()
        if (!startDate.isAfter(current.second)) {
            throw IllegalArgumentException("The new season must start after the current season starts")
        }

        val nextSeasonId = createSeason(current.first, startDate)
        val followingResetDate = if (nextResetDate <= startDate) {
            LocalDate.of(startDate.year + 1, 1, 1)
        } else {
            nextResetDate
        }
        if (followingResetDate != nextResetDate) updateResetDate(followingResetDate)
        insertResetAudit(
            actorNavNoEmail,
            "SEASON_RESET_MANUAL",
            nextSeasonId,
            reason,
            current.second,
            startDate,
            nextResetDate,
            followingResetDate,
        )
        return currentSeason()
    }

    @Transactional
    override fun resetIfDue(today: LocalDate): Boolean {
        val dueDate = lockSettings()
        if (dueDate.isAfter(today)) return false

        val current = lockCurrentSeason()
        if (!dueDate.isAfter(current.second)) {
            throw IllegalStateException("The scheduled reset date must be after the current season start")
        }
        val startDate = dueDate
        val nextSeasonId = createSeason(current.first, startDate)
        val followingResetDate = LocalDate.of(startDate.year + 1, 1, 1)
        updateResetDate(followingResetDate)
        insertResetAudit(
            actorNavNoEmail = null,
            action = "SEASON_RESET_SCHEDULED",
            seasonId = nextSeasonId,
            reason = null,
            previousStart = current.second,
            newStart = startDate,
            previousResetDate = dueDate,
            nextResetDate = followingResetDate,
        )
        return true
    }

    private fun lockSettings(): LocalDate =
        jdbcTemplate.queryForObject(
            """
                SELECT next_reset_date
                FROM program_season_settings
                WHERE singleton = TRUE
                FOR UPDATE
            """.trimIndent(),
            LocalDate::class.java,
        ) ?: error("No season reset date is configured")

    private fun lockCurrentSeason(): Pair<UUID, LocalDate> =
        jdbcTemplate.queryForObject(
            "SELECT id, starts_on FROM program_seasons WHERE ends_on IS NULL FOR UPDATE",
            { rs, _ ->
                rs.getObject("id", UUID::class.java) to rs.getObject("starts_on", LocalDate::class.java)
            },
        )

    private fun createSeason(currentSeasonId: UUID, startDate: LocalDate): UUID {
        val newSeasonId = UUID.randomUUID()
        jdbcTemplate.update(
            "UPDATE program_seasons SET ends_on = ? WHERE id = ?",
            startDate.minusDays(1),
            currentSeasonId,
        )
        jdbcTemplate.update(
            "INSERT INTO program_seasons (id, starts_on) VALUES (?, ?)",
            newSeasonId,
            startDate,
        )
        return newSeasonId
    }

    private fun updateResetDate(date: LocalDate) {
        jdbcTemplate.update(
            "UPDATE program_season_settings SET next_reset_date = ?, updated_at = NOW() WHERE singleton = TRUE",
            date,
        )
    }

    private fun insertResetAudit(
        actorNavNoEmail: String?,
        action: String,
        seasonId: UUID,
        reason: String?,
        previousStart: LocalDate,
        newStart: LocalDate,
        previousResetDate: LocalDate,
        nextResetDate: LocalDate,
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (
                    actor_nav_no_email, action, affected_record_id, reason, before_values, after_values
                ) VALUES (
                    ?, ?, ?, ?, jsonb_build_object('seasonStartsOn', ?, 'nextResetDate', ?),
                    jsonb_build_object('seasonStartsOn', ?, 'nextResetDate', ?)
                )
            """.trimIndent(),
            actorNavNoEmail,
            action,
            seasonId,
            reason,
            previousStart,
            previousResetDate,
            newStart,
            nextResetDate,
        )
    }

    private val scoreQuery = """
        WITH credit_totals AS (
            SELECT participant_id, SUM(points) AS points
            FROM activity_credits
            WHERE season_id = ?
            GROUP BY participant_id
        ),
        adjustment_totals AS (
            SELECT participant_id, SUM(points_delta) AS points
            FROM point_adjustments
            WHERE season_id = ?
            GROUP BY participant_id
        )
        SELECT
            participant.id AS participant_id,
            participant.fullname,
            participant.email,
            (participant.status = 'ACTIVE') AS active,
            COALESCE(credit_totals.points, 0) + COALESCE(adjustment_totals.points, 0) AS points
        FROM program_participants AS participant
        LEFT JOIN credit_totals ON credit_totals.participant_id = participant.id
        LEFT JOIN adjustment_totals ON adjustment_totals.participant_id = participant.id
        WHERE (? = FALSE OR participant.status = 'ACTIVE')
        ORDER BY LOWER(participant.fullname), participant.id
    """.trimIndent()
}
