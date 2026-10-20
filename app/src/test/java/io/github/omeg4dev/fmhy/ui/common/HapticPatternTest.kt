package io.github.omeg4dev.fmhy.ui.common

import android.os.VibrationEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternTest {

    @Test
    fun everyKindHasItsOwnPrimitivePattern() {
        val patterns = HapticKind.entries.map { primitivePattern(it) }
        assertEquals(HapticKind.entries.size, patterns.toSet().size)
    }

    @Test
    fun everyKindHasItsOwnPulseTiming() {
        // Timings alone must differ: vibrators without amplitude control ignore the amplitudes.
        val timings = HapticKind.entries.map { pulsePattern(it).timings.toList() }
        assertEquals(HapticKind.entries.size, timings.toSet().size)
    }

    @Test
    fun pulsePatternsAreWellFormed() {
        HapticKind.entries.forEach { kind ->
            val p = pulsePattern(kind)
            assertEquals(kind.name, p.timings.size, p.amplitudes.size)
            assertTrue(kind.name, p.timings.size % 2 == 0)
            assertTrue(kind.name, p.amplitudes.all { it in 0..255 })
            assertTrue(kind.name, p.timings.sum() in 1..400)
        }
    }

    @Test
    fun primitiveIdsMatchPlatformConstants() {
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, HapticPrimitive.CLICK.id)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_THUD, HapticPrimitive.THUD.id)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, HapticPrimitive.QUICK_RISE.id)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, HapticPrimitive.QUICK_FALL.id)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_TICK, HapticPrimitive.TICK.id)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, HapticPrimitive.LOW_TICK.id)
    }
}
