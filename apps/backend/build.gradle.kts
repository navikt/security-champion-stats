import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    application
    java
    kotlin("plugin.serialization") version "2.2.21"
    id("org.springframework.boot") version "3.5.8"
    kotlin("plugin.spring") version "2.2.21"
}

group = "no.navikt.appsec.securitychampionstats"

dependencies {

    //bundles
    implementation(libs.bundles.spring)
    implementation(libs.bundles.flyway)

    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")

    // singles
    implementation(libs.swagger)
    implementation(libs.slack.api)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.postgres)
    implementation(libs.bundles.jackson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "navikt.appsec.securitychampionstats.AppStarterKt"
}

tasks {
    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = "navikt.appsec.securitychampionstats.AppStarterKt"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists()) it.copyTo(file)
            }
        }
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
        }
    }

    withType<Wrapper> {
        gradleVersion = "8.14.2"
    }
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}