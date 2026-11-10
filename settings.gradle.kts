// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.

pluginManagement {
    // KSP publishes its plugin marker only to the Gradle Plugin Portal, so point the plugin ID
    // straight at the official artifact on Maven Central instead.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        // Official sources only: Google Maven (AndroidX, AGP) and Maven Central (Kotlin, KSP, JetBrains).
        google()
        mavenCentral()
        gradlePluginPortal {
            content { includeGroupByRegex("org\\.gradle.*") }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PocketBox"
include(":app")
