package navikt.appsec.securitychampionapp.app.scoring

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

/** A current-season credit with the points it is worth today, including earlier rule-driven corrections. */
data class PricedCredit(
    val id: UUID,
    val participantId: UUID,
    val creditType: ActivityCreditType,
    val effectivePoints: Long,
)

data class CreditRepricing(val id: UUID, val participantId: UUID, val delta: Int)

data class ScoringConfigurationChanges(
    val credits: List<CreditRepricing>,
    val preview: ScoringConfigurationPreview,
) {
    companion object {
        /**
         * Works out what a configuration change does to the season. Credits are only repriced when the
         * request is retroactive; the preview token fingerprints every input so a stale preview is rejected.
         */
        fun calculate(
            request: ScoringConfigurationRequest,
            before: ScoringConfiguration,
            season: SeasonSummary,
            pricedCredits: List<PricedCredit>,
            scores: List<ParticipantSeasonScore>,
        ): ScoringConfigurationChanges {
            val desiredPoints = request.activities.associate { it.creditType to it.points }
            val credits = if (request.applyRetroactively) {
                pricedCredits.sortedBy { it.id }.map {
                    CreditRepricing(
                        it.id,
                        it.participantId,
                        Math.toIntExact(desiredPoints.getValue(it.creditType).toLong() - it.effectivePoints),
                    )
                }.filter { it.delta != 0 }
            } else {
                emptyList()
            }
            val deltas = credits.groupBy { it.participantId }
                .mapValues { (_, values) -> values.sumOf { it.delta.toLong() } }
            val after = ScoringConfiguration(before.version + 1, request.tiers, request.activities)
            val sortedScores = scores.sortedBy { it.participantId }
            val impacts = sortedScores.map {
                val pointsAfter = it.points + (deltas[it.participantId] ?: 0L)
                ScoringImpact(
                    it.participantId, it.fullName, it.points, pointsAfter,
                    before.levelFor(it.points), after.levelFor(pointsAfter),
                )
            }.filter { it.pointsBefore != it.pointsAfter || it.levelBefore != it.levelAfter }
            return ScoringConfigurationChanges(
                credits,
                ScoringConfigurationPreview(
                    previewToken(request, before, season, credits, sortedScores),
                    season,
                    credits.size,
                    credits.sumOf { it.delta.toLong() },
                    impacts,
                ),
            )
        }

        private fun previewToken(
            request: ScoringConfigurationRequest,
            before: ScoringConfiguration,
            season: SeasonSummary,
            credits: List<CreditRepricing>,
            scores: List<ParticipantSeasonScore>,
        ): String {
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
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
