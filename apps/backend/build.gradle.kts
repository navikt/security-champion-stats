import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
    id("org.springframework.boot") version "4.1.1"
    kotlin("plugin.spring") version "2.4.21"
}

group = "no.navikt.appsec.securitychampionstats"

dependencies {
    implementation(libs.bundles.spring) {
        exclude("org.springframework.boot", "spring-boot-starter-tomcat")
    }

    implementation(libs.bundles.flyway)

    implementation(kotlin("reflect"))

    implementation(libs.jackson.module)
    implementation(libs.swagger)
    implementation(libs.slack.api)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.postgres)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
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
        gradleVersion = "9.7.1"
    }
}