package io.github.omeg4dev.fmhy

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @Test
    fun smokeTest() {
        assertEquals(4, 2 + 2)
    }
}
