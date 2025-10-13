pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    val kotlinVersion = "2.0.21"
    val agpVersion = "8.6.1"
    val composeVersion = "1.7.0"
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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
    ":tools"
)

fun iInclude(vararg paths: String) {
    paths.forEach { include(it) }
}
