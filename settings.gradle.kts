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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SmartClick"
include(":app")
include(":core:model")
include(":core:tv-api")
include(":tv:core")
include(":tv:samsung")
include(":tv:androidtv")
include(":tv:lg")
include(":tv:roku")
include(":tv:sony")
include(":tv:vizio")
include(":tv:firetv")
include(":tv:dial")
include(":tv:cast")
include(":tv:vidaa")
