import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    id("io.freefair.lombok") version "9.5.0"
    application
    java
    kotlin("plugin.serialization") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    kotlin("plugin.spring") version "2.2.21"
}

group = "no.navikt.appsec.securitychampionstats"

dependencies {

    implementation(libs.bundles.spring)
    implementation(libs.bundles.flyway)

    implementation(libs.swagger)
    implementation(libs.slack.api)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.postgres)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.spring.webmc)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "navikt.appsec.securitychampionapp.ApplicationKt"
    applicationName = "app"
}

tasks {

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
        }
    }

    withType<Wrapper> {
        gradleVersion = "9.0.0"
    }
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}
