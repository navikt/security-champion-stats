package navikt.appsec.securitychampionapp.app.jobs

import navikt.appsec.securitychampionapp.app.scoring.SlackScoringService
import navikt.appsec.securitychampionapp.app.scoring.SlackSyncSummary
import navikt.appsec.securitychampionapp.integrations.postgress.PostgresJobLock
import navikt.appsec.securitychampionapp.integrations.postgress.SlackScoringStatusRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.config.CronTask
import java.time.Clock

class SlackSchedulingTest {
    private val runner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withUserConfiguration(JobConfiguration::class.java)
        .withPropertyValues("spring.config.location=file:src/main/resources/application.yaml")

    @Test
    fun `should retain the six-hour production Slack schedule`() {
        runner.withPropertyValues("spring.profiles.active=prod", "SLACK_SC_CHANNEL_ID=C_PROD").run { context ->
            assertThat(context).hasNotFailed()
            val scheduledTasks = context.getBean(ScheduledAnnotationBeanPostProcessor::class.java).scheduledTasks
            assertThat(scheduledTasks).hasSize(1)
            assertThat(scheduledTasks.single().task).isInstanceOf(CronTask::class.java)
            assertThat((scheduledTasks.single().task as CronTask).expression).isEqualTo("0 0 */6 * * *")
        }
    }

    @Test
    fun `should disable local scheduling while retaining real manual sync configuration`() {
        runner.withPropertyValues(
            "spring.profiles.active=local",
            "SLACK_TOKEN=synthetic-slack-token",
            "SLACK_SC_CHANNEL_ID=C_LOCAL",
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java).scheduledTasks).isEmpty()
            assertThat(context.environment.getProperty("slack.token")).isEqualTo("synthetic-slack-token")
            val trigger = context.getBean(ScoringSyncTrigger::class.java)
            val scoring = context.getBean(SlackScoringService::class.java)
            whenever(scoring.sync(eq("C_LOCAL"), any())).thenReturn(SlackSyncSummary(0, 0, 0, 0))
            whenever(trigger.trigger(eq(1_004L), eq("syncSlackScoring"), any())).thenAnswer {
                it.getArgument<() -> Unit>(2).invoke()
                SyncTriggerResult.STARTED
            }

            assertThat(context.getBean(SlackScoringSyncJob::class.java).triggerManualSync())
                .isEqualTo(SyncTriggerResult.STARTED)

            verify(scoring).sync(eq("C_LOCAL"), any())
        }
    }

    @Test
    fun `should disable Slack scheduling in the test profile`() {
        runner.withPropertyValues(
            "spring.profiles.active=test",
            "spring.config.additional-location=file:src/test/resources/",
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java).scheduledTasks).isEmpty()
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(SlackScoringSyncJob::class)
    class JobConfiguration {
        @Bean
        fun jobLock(): PostgresJobLock = mock()

        @Bean
        fun syncTrigger(): ScoringSyncTrigger = mock()

        @Bean
        fun scoringService(): SlackScoringService = mock()

        @Bean
        fun statusRepository(): SlackScoringStatusRepository = mock()

        @Bean
        fun clock(): Clock = Clock.systemUTC()
    }
}
