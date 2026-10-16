package navikt.appsec.securitychampionapp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("team-catalog.roles")
data class TeamCatalogRoleProperties(
    val enabled: Boolean = false,
    val dryRun: Boolean = true,
)

@Configuration
@EnableConfigurationProperties(TeamCatalogRoleProperties::class)
class TeamCatalogRoleConfig
