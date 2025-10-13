plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
