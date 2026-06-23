// Baseline-profile generator (Macrobenchmark). Run `./gradlew :app:generateBaselineProfile` with a
// device/emulator connected — it drives the app's cold-start journey and writes a method-level profile
// that the :app release build then bakes in (replacing the curated class-level baseline-prof.txt).
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "dev.kris.clyde.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // arm64 to match the app (a x86_64 test emulator runs the app via ARM translation).
        ndk { abiFilters += "arm64-v8a" }
    }

    // The app under test.
    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Generate against whatever device/emulator is connected via adb.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0-alpha06")
}
