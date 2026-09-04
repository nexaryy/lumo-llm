pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lumo"
include(":app")
include(":vosk-model")

// Baseline profile generation module — only included when the property
// `includeBaselineProfile=true` is set in ~/.gradle/gradle.properties or via -P.
// Skipped in CI / on machines without an emulator to keep the build green.
if (providers.gradleProperty("includeBaselineProfile").orNull == "true") {
    include(":baselineprofile")
}

