package io.github.omeg4dev.fmhy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugStrictModeTest {

    @Test
    fun shouldEnable_whenDebugBuildAndPropertyNotZero() {
        assertTrue(DebugStrictMode.shouldEnable(isDebugBuild = true, propertyValue = null))
        assertTrue(DebugStrictMode.shouldEnable(isDebugBuild = true, propertyValue = ""))
        assertTrue(DebugStrictMode.shouldEnable(isDebugBuild = true, propertyValue = "1"))
    }

    @Test
    fun shouldEnable_whenDebugBuildAndPropertyIsZero() {
        assertFalse(DebugStrictMode.shouldEnable(isDebugBuild = true, propertyValue = "0"))
        assertFalse(DebugStrictMode.shouldEnable(isDebugBuild = true, propertyValue = " 0\n"))
    }

    @Test
    fun shouldEnable_whenReleaseBuild() {
        assertFalse(DebugStrictMode.shouldEnable(isDebugBuild = false, propertyValue = null))
        assertFalse(DebugStrictMode.shouldEnable(isDebugBuild = false, propertyValue = "1"))
    }
}
