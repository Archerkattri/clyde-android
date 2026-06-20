plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.kris.clyde"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kris.clyde"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        textReport = true
        textOutput = file("stdout")
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Pin androidx to the last versions that compile against SDK 35 (newer activity/lifecycle
// 2.9+/1.12+ require compileSdk 36 + AGP 8.9). Keeps us on the installed toolchain.
configurations.all {
    resolutionStrategy {
        force(
            "androidx.activity:activity:1.9.3",
            "androidx.activity:activity-ktx:1.9.3",
            "androidx.activity:activity-compose:1.9.3",
            "androidx.lifecycle:lifecycle-common:2.8.7",
            "androidx.lifecycle:lifecycle-runtime:2.8.7",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7",
            "androidx.lifecycle:lifecycle-runtime-compose:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7",
            "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7",
            "androidx.lifecycle:lifecycle-service:2.8.7",
            "androidx.lifecycle:lifecycle-process:2.8.7",
            "androidx.lifecycle:lifecycle-livedata-core:2.8.7",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Clawd mascot (animated GIF rendering)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Frosted "liquid glass": in-app via Modifier.blur/RenderEffect (API 31+); the summon
    // overlay uses OS cross-window blur (FLAG_BLUR_BEHIND). No external glass lib needed.

    // Embedded loopback HTTP server (LocalControlServer on 127.0.0.1:8766)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Shizuku (Tier 2 — ADB-level control, no root)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
