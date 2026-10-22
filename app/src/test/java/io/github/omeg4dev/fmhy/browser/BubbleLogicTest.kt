package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.BrowserButtonStyle
import io.github.omeg4dev.fmhy.data.BubbleSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLogicTest {
    @Test fun nearestEdgeSnap() {
        assertEquals(BubbleSide.LEFT, BubbleLogic.nearestSide(x = 100f, bubbleSize = 100f, containerWidth = 1000f))
        assertEquals(BubbleSide.RIGHT, BubbleLogic.nearestSide(x = 700f, bubbleSize = 100f, containerWidth = 1000f))
        // exactly centred goes right
        assertEquals(BubbleSide.RIGHT, BubbleLogic.nearestSide(x = 450f, bubbleSize = 100f, containerWidth = 1000f))
        assertEquals(20f, BubbleLogic.edgeX(BubbleSide.LEFT, 100f, 1000f, 20f), 0f)
        assertEquals(880f, BubbleLogic.edgeX(BubbleSide.RIGHT, 100f, 1000f, 20f), 0f)
    }

    @Test fun yClampAndFractionRoundTrip() {
        assertEquals(200f, BubbleLogic.clampY(50f, 200f, 1000f), 0f)
        assertEquals(1000f, BubbleLogic.clampY(5000f, 200f, 1000f), 0f)
        assertEquals(600f, BubbleLogic.clampY(600f, 200f, 1000f), 0f)
        assertEquals(0.5f, BubbleLogic.yToFraction(600f, 200f, 1000f), 0.0001f)
        assertEquals(600f, BubbleLogic.fractionToY(0.5f, 200f, 1000f), 0.001f)
        for (f in listOf(0f, 0.25f, 0.75f, 1f)) {
            assertEquals(f, BubbleLogic.yToFraction(BubbleLogic.fractionToY(f, 123f, 987f), 123f, 987f), 0.0001f)
        }
        // degenerate range
        assertEquals(300f, BubbleLogic.fractionToY(0.75f, 300f, 300f), 0f)
        assertEquals(0f, BubbleLogic.yToFraction(10f, 300f, 300f), 0f)
    }

    @Test fun badgeText() {
        assertEquals("1", BubbleLogic.badgeText(1))
        assertEquals("9", BubbleLogic.badgeText(9))
        assertEquals("9+", BubbleLogic.badgeText(10))
        assertEquals("9+", BubbleLogic.badgeText(42))
    }

    @Test fun visibilityRule() {
        assertTrue(BubbleLogic.isVisible(1, BrowserButtonStyle.FLOATING, hidden = false))
        assertFalse(BubbleLogic.isVisible(0, BrowserButtonStyle.FLOATING, hidden = false))
        assertFalse(BubbleLogic.isVisible(3, BrowserButtonStyle.TOP_BAR, hidden = false))
        assertFalse(BubbleLogic.isVisible(3, BrowserButtonStyle.FLOATING, hidden = true))
    }
}

class BackBubbleLogicTest {
    @Test fun visibilityRule() {
        assertTrue(BubbleLogic.backBubbleVisible(hidden = false, fullscreen = false, pip = false, ime = false, editMode = false))
        assertFalse(BubbleLogic.backBubbleVisible(hidden = true, fullscreen = false, pip = false, ime = false, editMode = false))
        assertFalse(BubbleLogic.backBubbleVisible(false, fullscreen = true, pip = false, ime = false, editMode = false))
        assertFalse(BubbleLogic.backBubbleVisible(false, false, pip = true, ime = false, editMode = false))
        assertFalse(BubbleLogic.backBubbleVisible(false, false, false, ime = true, editMode = false))
        assertFalse(BubbleLogic.backBubbleVisible(false, false, false, false, editMode = true))
        assertFalse(BubbleLogic.backBubbleVisible(false, false, false, false, false, findMode = true))
    }

    @Test fun rangeAndFractionForBackBubble() {
        // 2400 px tall, status bar 100, nav 60, bottom toolbar 170, bubble 105, margin 21
        val minY = BubbleLogic.backBubbleMinY(100f, 0f, 21f)
        val maxY = BubbleLogic.backBubbleMaxY(2400f, 60f, 170f, 105f, 21f)
        assertEquals(121f, minY, 0f)
        assertEquals(2044f, maxY, 0f)
        // default 0.85 lands inside the range; clamp and round trip
        val y = BubbleLogic.fractionToY(0.85f, minY, maxY)
        assertTrue(y in minY..maxY)
        assertEquals(0.85f, BubbleLogic.yToFraction(y, minY, maxY), 0.0001f)
        assertEquals(minY, BubbleLogic.clampY(-50f, minY, maxY), 0f)
        assertEquals(maxY, BubbleLogic.clampY(99999f, minY, maxY), 0f)
        // top toolbar pushes the allowed top down
        assertEquals(291f, BubbleLogic.backBubbleMinY(100f, 170f, 21f), 0f)
    }

    @Test fun sideSnapAndHiddenFraction() {
        assertEquals(BubbleSide.LEFT, BubbleLogic.nearestSide(10f, 105f, 1080f))
        assertEquals(BubbleSide.RIGHT, BubbleLogic.nearestSide(900f, 105f, 1080f))
        assertEquals(0f, BubbleLogic.barHiddenFraction(0f, 170f), 0f)
        assertEquals(0.5f, BubbleLogic.barHiddenFraction(-85f, 170f), 0.0001f)
        assertEquals(1f, BubbleLogic.barHiddenFraction(500f, 170f), 0f)
        assertEquals(0f, BubbleLogic.barHiddenFraction(10f, 0f), 0f)
    }
}
