pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroup("org.jetbrains.compose")
            }
        }
    }
    val kotlinVersion = "2.0.21"
    val agpVersion = "8.13.0"
    val composeVersion = "1.10.0+dev3103"
    val sqldelightVersion = "2.0.2"
    val detektVersion = "1.23.7"
    val spotlessVersion = "6.25.0"
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
        id("org.jetbrains.kotlin.android") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
        id("com.android.application") version agpVersion
        id("com.android.library") version agpVersion
        id("org.jetbrains.compose") version composeVersion
        id("app.cash.sqldelight") version sqldelightVersion
        id("io.gitlab.arturbosch.detekt") version detektVersion
        id("com.diffplug.spotless") version spotlessVersion
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            content {
                includeGroup("org.jetbrains.compose")
            }
        }
    }
}

rootProject.name = "jellystack-mobile"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

iInclude(
    ":app-android",
    ":app-ios",
    ":shared-core",
    ":shared-network",
    ":shared-database",
    ":players",
    ":design",
    ":testing",
    ":tools",
)

fun iInclude(vararg paths: String) {
    paths.forEach { include(it) }
}
