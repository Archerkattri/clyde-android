import org.gradle.api.tasks.bundling.Tar
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

// Bundle the brain's source into the APK (as assets/brain.tgz) so the app can deliver it to Termux
// over loopback during setup — no remote repo, always in sync with the app. node_modules/.env excluded.
val bundleBrain by tasks.registering(Tar::class) {
    archiveFileName.set("brain.tgz")
    compression = org.gradle.api.tasks.bundling.Compression.GZIP
    destinationDirectory.set(layout.projectDirectory.dir("src/main/assets"))
    from(rootProject.layout.projectDirectory.dir("brain")) {
        into("brain")
        exclude("node_modules/**", "dist/**", ".env", ".env.local", "*.log")
    }
}
tasks.named("preBuild") { dependsOn(bundleBrain) }

// Release signing: read app/keystore.properties if present (gitignored). Generate your own keystore
// and point this file at it for distribution; without it, release stays unsigned (CI/dev still builds).
val keystorePropsFile = project.file("keystore.properties")
val keystoreProps = Properties().apply { if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) } }

android {
    namespace = "dev.kris.clyde"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.kris.clyde"
        minSdk = 31
        targetSdk = 36
        versionCode = 39
        versionName = "0.1.38"
        vectorDrawables { useSupportLibrary = true }
        // arm64-only by design (the embedded brain's libnode.so is arm64; the bootstrap is aarch64).
        // The x86_64 UI-test emulator runs arm64 via translation, so this doesn't affect that path.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
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
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Extract jniLibs to nativeLibraryDir as real files on disk — node is exec'd from there (the one
        // location Android allows executing app code from). Without this, libs stay mmap'd in the APK
        // and there's no file to execve. These ELFs are already stored uncompressed-friendly.
        jniLibs { useLegacyPackaging = true }
    }
}

// Modern Kotlin compiler DSL (kotlinOptions{} is removed under Kotlin 2.4 / AGP 9).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Latest Compose/AndroidX that target the stable compileSdk 36 (the 37-only releases —
    // Compose 1.11.x / lifecycle 2.11.0 — are skipped since API 37 is a preview platform).
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-text-google-fonts")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Clawd the mascot is drawn natively in Compose (overlay/ClawdView) — no image-loading or GIF
    // library, so he ships in every build and can never go missing.

    // Frosted "liquid glass": in-app via Modifier.blur/RenderEffect (API 31+); the summon
    // overlay uses OS cross-window blur (FLAG_BLUR_BEHIND). No external glass lib needed.

    // Embedded loopback HTTP server (LocalControlServer on 127.0.0.1:8766)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Shizuku (Tier 2 — ADB-level control, no root)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Consumes the generated startup profile from :baselineprofile (./gradlew :app:generateBaselineProfile)
    baselineProfile(project(":baselineprofile"))
}
