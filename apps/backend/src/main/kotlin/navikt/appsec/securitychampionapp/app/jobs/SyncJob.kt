package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.app.participation.ParticipantStore
import navikt.appsec.securitychampionapp.integrations.teamCatalog.TeamCatalog
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val SYNC_JOB_LOCK_KEY = 1_001L

@Component
class SyncJob(
    private val jobLock: PostgresJobLock,
    private val repo: ParticipantStore,
    private val catalog: TeamCatalog,
    private val auditService: ProgramAuditService? = null,
) {
    private val logger = LoggerFactory.getLogger(SyncJob::class.java)

    @Scheduled(cron = "0 0 12 */1 * *")
    fun syncDatabase() {
        jobLock.runWithLock(SYNC_JOB_LOCK_KEY, "syncDatabase") {
            val run = AuditRunContext()
            auditService?.recordRun("PARTICIPANT_PROFILE_SYNC_STARTED", AuditOutcome.SUCCEEDED, run)
            val catalogMembers = catalog.fetchAllMembersWithTeamData()
            if (catalogMembers.isEmpty()) {
                auditService?.recordRun(
                    "PARTICIPANT_PROFILE_SYNC_FAILED",
                    AuditOutcome.FAILED,
                    run,
                    mapOf("failure" to "emptyCatalog"),
                )
                logger.warn("Skipping participant profile sync because Teamkatalogen returned no members")
                return@runWithLock
            }

            val participants = repo.findAllParticipants()
            var updated = 0
            var failed = 0
            catalogMembers.forEach { catalogMember ->
                val hasParticipant = participants.any {
                    it.navIdent == catalogMember.navIdent && it.email == catalogMember.email
                }
                if (hasParticipant) {
                    try {
                        updated += repo.updateProfile(
                            navIdent = catalogMember.navIdent,
                            email = catalogMember.email,
                            fullname = catalogMember.fullName,
                            teams = catalogMember.teamName,
                        )
                    } catch (e: Exception) {
                        failed++
                        logger.error(
                            "Failed to update participant profile during Teamkatalogen sync; continuing (cause={})",
                            e.javaClass.simpleName,
                        )
                    }
                }
            }
            auditService?.recordRun(
                "PARTICIPANT_PROFILE_SYNC_COMPLETED",
                if (failed == 0) AuditOutcome.SUCCEEDED else AuditOutcome.PARTIAL,
                run,
                mapOf("profilesUpdated" to updated, "failedProfiles" to failed),
            )
        }
    }
}
