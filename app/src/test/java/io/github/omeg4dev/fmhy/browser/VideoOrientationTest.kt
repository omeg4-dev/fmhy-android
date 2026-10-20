package io.github.omeg4dev.fmhy.browser

import android.content.pm.ActivityInfo
import io.github.omeg4dev.fmhy.data.FullscreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoOrientationTest {
    private val landscape = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    private val portrait = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

    @Test
    fun autoFollowsTheVideoShape() {
        assertEquals(portrait, fullscreenRequestedOrientation(FullscreenOrientation.AUTO, 0.5f))
        assertEquals(landscape, fullscreenRequestedOrientation(FullscreenOrientation.AUTO, 1.78f))
        assertEquals(landscape, fullscreenRequestedOrientation(FullscreenOrientation.AUTO, 1f))
        assertEquals(landscape, fullscreenRequestedOrientation(FullscreenOrientation.AUTO, null))
    }

    @Test
    fun landscapeIgnoresTheVideoShape() {
        assertEquals(landscape, fullscreenRequestedOrientation(FullscreenOrientation.LANDSCAPE, 0.5f))
    }

    @Test
    fun followPhoneChangesNothing() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            fullscreenRequestedOrientation(FullscreenOrientation.FOLLOW_PHONE, 1.78f),
        )
    }
}
