package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.scoring.ActivityCredit
import navikt.appsec.securitychampionapp.app.scoring.ActivityCreditType
import navikt.appsec.securitychampionapp.app.scoring.CreditAwardResult
import navikt.appsec.securitychampionapp.app.scoring.ParticipantSeasonScore
import navikt.appsec.securitychampionapp.app.scoring.PointAdjustment
import navikt.appsec.securitychampionapp.app.scoring.SeasonSummary
import navikt.appsec.securitychampionapp.app.scoring.ScoringTargetNotFoundException
import navikt.appsec.securitychampionapp.app.scoring.SourceCreditNotFoundException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Repository
class ScoringRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
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

    fun currentSeason(): SeasonSummary =
        jdbcTemplate.queryForObject(
            """
                SELECT season.id, season.starts_on, season.ends_on, settings.next_reset_date
                FROM program_seasons AS season
                CROSS JOIN program_season_settings AS settings
                WHERE season.ends_on IS NULL AND settings.singleton = TRUE
            """.trimIndent(),
            seasonMapper,
        )

    fun scoresForCurrentSeason(activeOnly: Boolean = false): List<ParticipantSeasonScore> =
        scoresForSeason(currentSeason().id, activeOnly)

    fun scoresForSeason(
        seasonId: UUID,
        activeOnly: Boolean = false,
    ): List<ParticipantSeasonScore> =
        jdbcTemplate.query(
            scoreQuery,
            scoreMapper,
            seasonId,
            seasonId,
            activeOnly,
        )

    fun scoreForParticipant(participantId: UUID, seasonId: UUID): Long =
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

    fun creditsForParticipant(participantId: UUID): List<ActivityCredit> =
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

    fun participantExists(participantId: UUID): Boolean =
        jdbcTemplate.query(
            "SELECT id FROM program_participants WHERE id = ?",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            participantId,
        ).isNotEmpty()

    @Transactional
    fun awardCredit(
        participantId: UUID,
        creditType: ActivityCreditType,
        uniquenessKey: String,
        sourceReference: String,
    ): CreditAwardResult {
        val seasonId = jdbcTemplate.queryForObject(
            "SELECT id FROM program_seasons WHERE ends_on IS NULL FOR SHARE",
            UUID::class.java,
        ) ?: error("No current program season exists")
        val inserted = jdbcTemplate.update(
            """
                INSERT INTO activity_credits (
                    id, participant_id, season_id, credit_type, uniqueness_key, source_reference, points
                )
                SELECT ?, participant.id, ?, ?, ?, ?, ?
                FROM program_participants AS participant
                WHERE participant.id = ? AND participant.status = 'ACTIVE'
                ON CONFLICT (participant_id, credit_type, uniqueness_key) DO NOTHING
            """.trimIndent(),
            UUID.randomUUID(),
            seasonId,
            creditType.name,
            uniquenessKey,
            sourceReference,
            creditType.points,
            participantId,
        )
        if (inserted == 1) return CreditAwardResult.AWARDED

        val duplicate = jdbcTemplate.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1 FROM activity_credits
                    WHERE participant_id = ? AND credit_type = ? AND uniqueness_key = ?
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
    fun addAdjustment(
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
    fun updateNextResetDate(
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
    fun resetManually(
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
    fun resetIfDue(today: LocalDate): Boolean {
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
