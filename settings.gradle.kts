rootProject.name = "security-champion-stats"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("apps/backend/gradle/libs.versions.toml"))
        }
        }
}

include(":backend")
project(":backend").projectDir = file("apps/backend")