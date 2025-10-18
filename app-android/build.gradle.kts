plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose)
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.jellystack.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.jellystack.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.sharedCore)
    implementation(projects.sharedNetwork)
    implementation(projects.sharedDatabase)
    implementation(projects.players)
    implementation(projects.design)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.serialization.json)
    implementation(libs.compose.navigation)
    implementation(libs.sqldelight.android)
    implementation(libs.coroutines.android)
    implementation(libs.koin.compose)
    implementation(libs.napier)
    implementation(libs.multiplatform.settings)
    implementation(libs.koin.android)
    implementation(libs.androidx.media3.ui)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
