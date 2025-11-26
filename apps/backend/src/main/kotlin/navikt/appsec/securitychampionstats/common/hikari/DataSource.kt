package navikt.appsec.securitychampionstats.common.hikari

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class DataSource(
    @Value($$"${spring.datasource.url}") private val dbUrl: String,
    @Value($$"${spring.datasource.username}") private val dbUsername: String,
    @Value($$"${spring.datasource.password}") private val dbPassword: String
) {

    @Bean
    open fun dataSourceHikariConfig(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = dbUrl
            username = dbUsername
            password = dbPassword
            driverClassName = "org.postgresql.Driver"

            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 5_000
            validationTimeout = 3_000
            leakDetectionThreshold = 10_000
        }

        return HikariDataSource(config)
    }
}