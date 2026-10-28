package io.github.omeg4dev.fmhy.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-01 scenarios. `None` = no AOT (first run after install, F-Droid without a profile),
 * `Profile` = with the checked-in baseline profile. Compare runs on the same device only.
 */
@RunWith(AndroidJUnit4::class)
class PerfBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val none = CompilationMode.None()
    private val profile = CompilationMode.Partial(BaselineProfileMode.Require)

    @Test fun startupNone() = startup(none)
    @Test fun startupProfile() = startup(profile)
    @Test fun homeScrollNone() = frames(none) { scrollHome() }
    @Test fun homeScrollProfile() = frames(profile) { scrollHome() }
    @Test fun sectionScrollNone() = frames(none, setup = { openBigSection() }) { scrollSection() }
    @Test fun sectionScrollProfile() = frames(profile, setup = { openBigSection() }) { scrollSection() }
    @Test fun navigateNone() = frames(none) { navigate() }
    @Test fun navigateProfile() = frames(profile) { navigate() }
    @Test fun searchNone() = frames(none) { searchAndScroll() }
    @Test fun searchProfile() = frames(profile) { searchAndScroll() }

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForHome()
    }

    private fun frames(
        mode: CompilationMode,
        setup: MacrobenchmarkScope.() -> Unit = {},
        measure: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // A startup mode would kill the app between setupBlock and measureBlock.
        startupMode = null,
        iterations = 5,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            waitForHome()
            setup()
        },
        measureBlock = measure,
    )
}

private fun MacrobenchmarkScope.navigate() {
    repeat(3) {
        openBigSection()
        backToHome()
    }
}
