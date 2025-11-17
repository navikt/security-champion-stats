plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization") version "2.2.21"
}

group = "no.navikt.appsec.securitychampionstats"
version = "1.0-SNAPSHOT"

val ktorVersion = "3.3.2"
val slf4jVersion = "2.0.17"
val logbackVersion = "1.5.21"
val hikariVersion = "7.0.2"
val postgresVersion = "42.7.8"
val jacksonVersion = "2.20.1"
val slackVersion = "1.46.0"

dependencies {

    //ktor server
    implementation("io.ktor:ktor-server-core:${ktorVersion}")
    implementation("io.ktor:ktor-server-cors:${ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${ktorVersion}")
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")
    implementation("io.ktor:ktor-server-call-logging:${ktorVersion}")
    implementation("io.ktor:ktor-server-auth:${ktorVersion}")
    implementation("io.ktor:ktor-server-auth-jwt:${ktorVersion}")

    //ktor client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // logging
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")

    // postgres
    implementation("com.zaxxer:HikariCP:${hikariVersion}")
    implementation("org.postgresql:postgresql:${postgresVersion}")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Slack
    implementation("com.slack.api:slack-api-client:$slackVersion")

    runtimeOnly("ch.qos.logback:logback-classic:${logbackVersion}")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "navikt.appsec.securitychampionstats.backend.Server"
}