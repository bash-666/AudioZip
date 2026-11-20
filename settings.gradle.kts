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
        // ffmpeg-kit is no longer served by Maven Central; use Google's mirror of it.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
    }
}

rootProject.name = "compresor-audio"
include(":app")
