@file:Suppress("UnstableApiUsage")

rootProject.name = "services"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

includeBuild("proto")
includeBuild("user-management")
includeBuild("tenant")
includeBuild("meeting-management")
includeBuild("meet")
includeBuild("record")
includeBuild("chat-management")
includeBuild("notification")
