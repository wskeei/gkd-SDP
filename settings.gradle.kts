rootProject.name = "gkd"
include(
    ":app",
    ":hidden_api",
    ":selector",
    ":quality-lint",
)

apply(from = "gradle/security-dependency-policy.settings.gradle.kts")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://jitpack.io")
    }
}
