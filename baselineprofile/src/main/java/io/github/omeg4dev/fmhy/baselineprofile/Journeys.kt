package io.github.omeg4dev.fmhy.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

const val PACKAGE = "io.github.omeg4dev.fmhy"

// The journeys assume onboarding is finished and the dataset is loaded (do it once by hand on the
// device; macrobenchmark keeps app data between runs).
private const val TIMEOUT = 5_000L
private const val BIG_SECTION = "Movies / TV / Anime"

fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.text(BIG_SECTION)), TIMEOUT)) {
        "Home list not shown: finish onboarding and the first sync on the device first"
    }
}

private fun MacrobenchmarkScope.find(text: String): UiObject2 =
    device.wait(Until.findObject(By.text(text)), TIMEOUT) ?: error(
        "\"$text\" not on screen; visible: " +
            device.findObjects(By.textContains("")).mapNotNull { it.text }.take(25).joinToString(" | ")
    )

// Home nests its list inside a full-screen scrollable (the tab pager), and smaller scrollables
// (chip rows) appear further down; the list is the tallest one that isn't the full-screen pager.
private fun MacrobenchmarkScope.list(): UiObject2 {
    device.wait(Until.hasObject(By.scrollable(true)), TIMEOUT)
    val all = device.findObjects(By.scrollable(true))
    val inner = all.filter { it.visibleBounds.top > 0 }.ifEmpty { all }
    return inner.maxByOrNull { it.visibleBounds.height() } ?: error("No scrollable list on screen")
}

private fun MacrobenchmarkScope.flingList(direction: Direction, times: Int) {
    repeat(times) {
        // Re-find each time: the node goes stale when the list recomposes.
        val list = list()
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(direction)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.scrollHome() {
    flingList(Direction.DOWN, 2)
    // Fling back until the big section shows again (fling()'s "can scroll more" result is unreliable
    // on Compose lists); the next journey step taps it.
    repeat(6) {
        if (device.hasObject(By.text(BIG_SECTION))) return
        flingList(Direction.UP, 1)
    }
}

// The node can go stale between finding and clicking while the list still settles; find it again once.
private fun MacrobenchmarkScope.click(text: String) {
    try {
        find(text).click()
    } catch (_: StaleObjectException) {
        find(text).click()
    }
}

fun MacrobenchmarkScope.openBigSection() {
    click(BIG_SECTION)
    device.wait(Until.gone(By.text("Random pick")), TIMEOUT)
    device.waitForIdle()
}

fun MacrobenchmarkScope.scrollSection() {
    flingList(Direction.DOWN, 5)
    flingList(Direction.UP, 2)
}

fun MacrobenchmarkScope.backToHome() {
    device.pressBack()
    waitForHome()
}

fun MacrobenchmarkScope.searchAndScroll() {
    click("Search")
    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), TIMEOUT)
        ?: error("No search field")
    field.click()
    field.text = "video"
    device.waitForIdle()
    device.pressBack() // hide the keyboard so the results list gets the whole screen
    flingList(Direction.DOWN, 4)
}
