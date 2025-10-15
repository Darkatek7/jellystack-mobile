import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.sharedCore)
                implementation(projects.sharedNetwork)
                implementation(libs.coroutines.core)
                implementation(libs.multiplatform.settings)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.multiplatform.settings)
                implementation(libs.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.coroutines.android)
                implementation(libs.androidx.media3.exoplayer)
                implementation(libs.androidx.media3.hls)
            }
        }
    }
}

android {
    namespace = "dev.jellystack.players"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
