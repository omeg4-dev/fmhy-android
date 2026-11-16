package io.github.omeg4dev.fmhy.browser

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCaptureTest {

    @Test
    fun planCaptureStandardMultiTile() {
        val plan = planCapture(
            viewWidth = 1080,
            viewHeight = 2000,
            contentHeight = 20_000,
            maxPixels = 12_000_000L,
        )

        assertFalse(plan.truncated)
        assertTrue(plan.outHeight <= 16_384)
        assertTrue(plan.outWidth.toLong() * plan.outHeight <= 12_000_000L)
        assertEquals(0, plan.offsets.first())
        assertEquals(18_000, plan.offsets.last())

        for (i in 0 until plan.offsets.size - 1) {
            val diff = plan.offsets[i + 1] - plan.offsets[i]
            assertTrue("Consecutive offset diff $diff should be <= 2000", diff <= 2000)
            assertTrue("Offsets should be strictly ascending", diff > 0)
        }

        // Verify covering [0, 20_000)
        var coveredUntil = 0
        for (offset in plan.offsets) {
            val crop = tileCrop(
                actualScrollY = offset,
                viewHeight = 2000,
                coveredUntil = coveredUntil,
                capturedHeight = plan.capturedHeight,
            )
            assertNotNull(crop)
            assertEquals(coveredUntil, crop!!.dstTop)
            coveredUntil = crop.dstTop + (crop.srcBottom - crop.srcTop)
        }
        assertEquals(20_000, coveredUntil)
    }

    @Test
    fun planCaptureTruncated() {
        val plan = planCapture(
            viewWidth = 1080,
            viewHeight = 2000,
            contentHeight = 100_000,
            maxPixels = 12_000_000L,
        )

        assertTrue(plan.truncated)
        assertEquals(60_000, plan.capturedHeight)
    }

    @Test
    fun planCaptureContentShorterThanView() {
        val plan = planCapture(
            viewWidth = 1080,
            viewHeight = 2000,
            contentHeight = 1500,
            maxPixels = 12_000_000L,
        )

        assertEquals(listOf(0), plan.offsets)
        assertEquals(1f, plan.scale, 0.0001f)
        assertEquals(2000, plan.outHeight)
    }

    @Test
    fun planCaptureNonMultipleHeightAndClamping() {
        val plan = planCapture(
            viewWidth = 1080,
            viewHeight = 2000,
            contentHeight = 5500,
            maxPixels = 12_000_000L,
        )

        assertEquals(listOf(0, 2000, 3500), plan.offsets)

        // Simulate loop with actualScrollY = offset
        var coveredUntil = 0
        val copiedRanges = mutableListOf<IntRange>()
        for (offset in plan.offsets) {
            val crop = tileCrop(
                actualScrollY = offset,
                viewHeight = 2000,
                coveredUntil = coveredUntil,
                capturedHeight = plan.capturedHeight,
            )
            assertNotNull(crop)
            val start = crop!!.dstTop
            val end = crop.dstTop + (crop.srcBottom - crop.srcTop)
            copiedRanges.add(start until end)
            coveredUntil = end
        }

        assertEquals(5500, coveredUntil)
        assertEquals(3, copiedRanges.size)
        assertEquals(0 until 2000, copiedRanges[0])
        assertEquals(2000 until 4000, copiedRanges[1])
        assertEquals(4000 until 5500, copiedRanges[2])

        // Verify no overlap and no gap
        for (i in 0 until copiedRanges.size - 1) {
            assertEquals(copiedRanges[i].last + 1, copiedRanges[i + 1].first)
        }

        // Simulate page clamping (actualScrollY smaller than requested)
        var clampedCoveredUntil = 0
        val clampedRanges = mutableListOf<IntRange>()

        // Tile 0 at requested 0
        val crop0 = tileCrop(0, 2000, clampedCoveredUntil, 5500)
        assertNotNull(crop0)
        clampedRanges.add(crop0!!.dstTop until (crop0.dstTop + crop0.srcBottom - crop0.srcTop))
        clampedCoveredUntil = crop0.dstTop + (crop0.srcBottom - crop0.srcTop)

        // Tile 1 at requested 2000
        val crop1 = tileCrop(2000, 2000, clampedCoveredUntil, 5500)
        assertNotNull(crop1)
        clampedRanges.add(crop1!!.dstTop until (crop1.dstTop + crop1.srcBottom - crop1.srcTop))
        clampedCoveredUntil = crop1.dstTop + (crop1.srcBottom - crop1.srcTop)

        // Tile 2 requested 3500, but clamped by browser to 3200
        val crop2 = tileCrop(3200, 2000, clampedCoveredUntil, 5500)
        assertNotNull(crop2)
        clampedRanges.add(crop2!!.dstTop until (crop2.dstTop + crop2.srcBottom - crop2.srcTop))
        clampedCoveredUntil = crop2.dstTop + (crop2.srcBottom - crop2.srcTop)

        // Verify each row in covered region [0, clampedCoveredUntil) was covered exactly once
        for (i in 0 until clampedRanges.size - 1) {
            assertEquals(clampedRanges[i].last + 1, clampedRanges[i + 1].first)
        }
        assertEquals(5200, clampedCoveredUntil)
    }

    @Test
    fun pictureFileNameFormatting() {
        val utc = ZoneId.of("UTC")
        val fixedTime = 1700000000000L // 2023-11-14T22:13:20Z

        assertEquals(
            "fmhy-example.com-20231114-221320.png",
            pictureFileName("https://www.example.com/x", fixedTime, utc),
        )

        assertEquals(
            "fmhy-192.168.1.1-20231114-221320.png",
            pictureFileName("http://192.168.1.1/", fixedTime, utc),
        )

        assertEquals(
            "fmhy-page-20231114-221320.png",
            pictureFileName("about:blank", fixedTime, utc),
        )

        assertEquals(
            "fmhy-foo-bar-123.org-20231114-221320.png",
            pictureFileName("https://foo_bar$123.org/page", fixedTime, utc),
        )

        assertEquals(
            "fmhy-page-20231114-221320.png",
            pictureFileName("not a valid url :///", fixedTime, utc),
        )
    }
}
