pluginManagement {
    includeBuild("build-logic")
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
        // LiveKit's audio routing library is only published on JitPack; nothing else may come from there.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.davidliu") }
        }
    }
}

rootProject.name = "xmuks"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:designsystem")
include(":core:protocol")
include(":core:network")
include(":core:database")
include(":core:account")
include(":core:data")
include(":core:richtext")
include(":core:notify")
include(":core:push")
include(":core:call")
include(":feature:login")
include(":feature:roomlist")
include(":feature:room")
include(":feature:media")
include(":feature:profile")
include(":feature:settings")
include(":feature:share")
include(":feature:call")
include(":wear")
include(":baselineprofile")
