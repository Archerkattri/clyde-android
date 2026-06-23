// Top-level build file. Plugin versions declared here, applied in :app.
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.test") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    // Baseline-profile generation (Macrobenchmark). Alpha track follows AGP 9; stable 1.4.x predates it.
    id("androidx.baselineprofile") version "1.5.0-alpha06" apply false
}
