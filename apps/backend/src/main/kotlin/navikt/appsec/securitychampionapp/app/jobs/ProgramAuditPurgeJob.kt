package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val PROGRAM_AUDIT_PURGE_LOCK_KEY = 1_007L

@Component
class ProgramAuditPurgeJob(
    private val jobLock: PostgresJobLock,
    private val auditService: ProgramAuditService,
) {
    @Scheduled(cron = "0 20 0 * * *")
    fun purgeExpiredOperationalEvents() {
        jobLock.runWithLock(PROGRAM_AUDIT_PURGE_LOCK_KEY, "purgeExpiredOperationalEvents") {
            auditService.purgeExpiredOperationalEvents()
        }
    }
}
