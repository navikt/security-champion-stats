package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.audit.AuditOutcome
import navikt.appsec.securitychampionapp.app.audit.AuditRunContext
import navikt.appsec.securitychampionapp.app.audit.ProgramAuditService
import navikt.appsec.securitychampionapp.app.membership.ChannelDepartureLimitException
import navikt.appsec.securitychampionapp.app.membership.SlackChannelParticipationOverview
import navikt.appsec.securitychampionapp.app.membership.SlackChannelParticipationService
import navikt.appsec.securitychampionapp.app.membership.SlackChannelParticipationSettings
import navikt.appsec.securitychampionapp.config.SlackChannelParticipationProperties
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.SlackChannelParticipationRepository
import navikt.appsec.securitychampionapp.integrations.slack.SlackIntegrationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

private const val SLACK_CHANNEL_PARTICIPATION_LOCK_KEY = 1_011L
private const val JOB_NAME = "checkSlackChannelParticipation"

@Component
class SlackChannelParticipationJob(
    private val lock: PostgresJobLock,
    private val trigger: ScoringSyncTrigger,
    private val service: SlackChannelParticipationService,
    private val repository: SlackChannelParticipationRepository,
    private val properties: SlackChannelParticipationProperties,
    private val settings: SlackChannelParticipationSettings,
    private val audit: ProgramAuditService,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(SlackChannelParticipationJob::class.java)

    @Scheduled(cron = $$"${slack.channel-participation.cron:0 0 9,15 * * *}", zone = "Europe/Oslo")
    fun scheduledCheck() {
        if (!properties.enabled) return
        lock.runWithLock(SLACK_CHANNEL_PARTICIPATION_LOCK_KEY, JOB_NAME) { runCheck(AuditRunContext()) }
    }

    fun triggerManualCheck(actor: String): SyncTriggerResult =
        if (!properties.enabled) SyncTriggerResult.DISABLED
        else trigger.trigger(SLACK_CHANNEL_PARTICIPATION_LOCK_KEY, JOB_NAME, actor, ::runCheck)

    fun overview(): SlackChannelParticipationOverview {
        val channelId = settings.channelId
        if (channelId.isBlank()) {
            return SlackChannelParticipationOverview(properties.enabled, false, null, null, null, null, emptyList())
        }
        val status = repository.status(channelId)
        val interrupted = status?.outcome == "RUNNING" && !lock.isLocked(SLACK_CHANNEL_PARTICIPATION_LOCK_KEY)
        return SlackChannelParticipationOverview(
            enabled = properties.enabled,
            channelConfigured = true,
            lastAttemptAt = status?.lastAttemptAt,
            lastSuccessAt = status?.lastSuccessAt,
            outcome = if (interrupted) "FAILED" else status?.outcome,
            failureSummary = if (interrupted) "Slack channel check was interrupted" else status?.failureSummary,
            participants = repository.attentionItems(channelId),
        )
    }

    private fun runCheck(run: AuditRunContext) {
        val channelId = settings.channelId
        audit.recordRun("SLACK_CHANNEL_CHECK_STARTED", AuditOutcome.SUCCEEDED, run)
        try {
            repository.recordStarted(channelId, clock.instant())
            val summary = service.check()
            summary.deactivated.forEach {
                audit.record(
                    "SLACK_CHANNEL_DEPARTURE_DEACTIVATED", AuditOutcome.SUCCEEDED,
                    targetParticipantId = it, correlationId = run.correlationId,
                )
            }
            summary.reactivated.forEach {
                audit.record(
                    "SLACK_CHANNEL_RETURN_REACTIVATED", AuditOutcome.SUCCEEDED,
                    targetParticipantId = it, correlationId = run.correlationId,
                )
            }
            val partial = summary.noticesOutstanding > 0
            repository.recordCompleted(channelId, clock.instant(), partial)
            audit.recordRun(
                "SLACK_CHANNEL_CHECK_COMPLETED", if (partial) AuditOutcome.PARTIAL else AuditOutcome.SUCCEEDED, run,
                mapOf(
                    "checkedParticipants" to summary.checkedParticipants,
                    "absent" to summary.absent,
                    "unresolvedIdentities" to summary.unresolved,
                    "deactivated" to summary.deactivated.size,
                    "reactivated" to summary.reactivated.size,
                    "noticesSent" to summary.noticesSent,
                    "noticesOutstanding" to summary.noticesOutstanding,
                ),
            )
            logger.info(
                "Slack channel check: checked={}, absent={}, unresolved={}, deactivated={}, reactivated={}, outstanding={}",
                summary.checkedParticipants, summary.absent, summary.unresolved,
                summary.deactivated.size, summary.reactivated.size, summary.noticesOutstanding,
            )
        } catch (e: SlackIntegrationException) {
            recordFailure(channelId, requireNotNull(e.message), run, "integration")
        } catch (e: ChannelDepartureLimitException) {
            recordFailure(channelId, requireNotNull(e.message), run, "departureLimit")
        } catch (_: IllegalStateException) {
            recordFailure(channelId, "Slack channel check refused an empty or inconsistent snapshot", run, "refused")
        } catch (_: DataAccessException) {
            recordFailure(channelId, "Slack channel check could not persist results", run, "persistence")
        } catch (_: IllegalArgumentException) {
            recordFailure(channelId, "Slack channel check configuration is incomplete", run, "configuration")
        } catch (e: Exception) {
            logger.error("Slack channel check failed unexpectedly", e)
            recordFailure(channelId, "Slack channel check failed unexpectedly", run, "unexpected")
        }
    }

    private fun recordFailure(channelId: String, summary: String, run: AuditRunContext, failure: String) {
        audit.recordRun("SLACK_CHANNEL_CHECK_FAILED", AuditOutcome.FAILED, run, mapOf("failure" to failure))
        try {
            if (channelId.isNotBlank()) repository.recordFailed(channelId, clock.instant(), summary)
        } catch (e: DataAccessException) {
            logger.error("Could not persist Slack channel check failure: {}", e.javaClass.simpleName)
        }
        logger.warn("Slack channel check failed: {}", summary)
    }
}
