plugins {
    kotlin("jvm") version "2.2.21"
    application
    kotlin("plugin.serialization") version "2.2.21"
}

group = "no.navikt.appsec.securitychampionstats"

dependencies {

    //bundles
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.postgres)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)

    // singles
    implementation(libs.slack.api)
    implementation(libs.swagger)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.ktor.server.test.host)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "navikt.appsec.securitychampionstats.server.Server"
}

tasks {
    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = "navikt.appsec.securitychampionstats.server.Server"
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