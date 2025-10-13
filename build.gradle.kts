plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

group = "dev.jellystack"
version = "0.0.1-SNAPSHOT"

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("config/detekt/detekt.yml").filter { it.exists() })
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
    }
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.register("printProjectStructure") {
    group = "help"
    description = "Prints included Gradle projects."
    doLast {
        println("Projects: ")
        rootProject.subprojects.sortedBy { it.path }.forEach { println(" - ${it.path}") }
    }
}
