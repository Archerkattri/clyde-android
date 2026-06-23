package dev.kris.clyde.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Clyde's startup baseline profile by driving a cold launch. The brain starts asynchronously,
 * so this captures the real UI startup path even on a x86_64 emulator (where the arm64 brain can't run).
 *
 * Run with a device/emulator connected:  ./gradlew :app:generateBaselineProfile
 * The result replaces the curated app/src/main/baseline-prof.txt with a method-level profile.
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = "dev.kris.clyde",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
