package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import io.github.omeg4dev.fmhy.R

data class OriginBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun isEmpty(): Boolean = width <= 0 || height <= 0
}

class BoundsHolder(var bounds: OriginBounds? = null)

enum class OpenStyle {
    GROW_FROM_BOUNDS,
    RISE,
    CROSSFADE,
}

const val OPEN_MS = 300
const val CLOSE_MS = 250
const val RISE_START_SCALE = 0.92f
const val APP_BEHIND_SCALE = 0.96f
const val DIM_ALPHA = 0.08f

fun chooseOpenStyle(originBounds: OriginBounds?, animationsOn: Boolean): OpenStyle {
    if (!animationsOn) return OpenStyle.CROSSFADE
    if (originBounds != null && !originBounds.isEmpty()) return OpenStyle.GROW_FROM_BOUNDS
    return OpenStyle.RISE
}

fun usePredictiveBack(action: BackAction, sdk: Int = Build.VERSION.SDK_INT): Boolean {
    return action == BackAction.RETURN_TO_FMHY && sdk >= 34
}

fun animationsOn(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    } catch (_: Throwable) {
        true
    }
}

// Window-bounds scale-up uses Activity.window.decorView as the root View with bounds relative to it.
// When bounds are absent or decorView is unavailable, falls back to custom RISE animation.
// When system animations are turned off, uses 150 ms crossfade.
fun browserOpenOptions(context: Context, originBounds: OriginBounds? = null): Bundle? {
    val animOn = animationsOn(context)
    return when (chooseOpenStyle(originBounds, animOn)) {
        OpenStyle.CROSSFADE -> {
            ActivityOptions.makeCustomAnimation(
                context,
                R.anim.browser_fade_in,
                R.anim.browser_fade_out,
            ).toBundle()
        }
        OpenStyle.GROW_FROM_BOUNDS -> {
            val decorView = (context as? Activity)?.window?.decorView
            if (decorView != null && originBounds != null) {
                ActivityOptions.makeScaleUpAnimation(
                    decorView,
                    originBounds.left,
                    originBounds.top,
                    originBounds.width,
                    originBounds.height,
                ).toBundle()
            } else {
                ActivityOptions.makeCustomAnimation(
                    context,
                    R.anim.browser_open_enter,
                    R.anim.browser_open_exit,
                ).toBundle()
            }
        }
        OpenStyle.RISE -> {
            ActivityOptions.makeCustomAnimation(
                context,
                R.anim.browser_open_enter,
                R.anim.browser_open_exit,
            ).toBundle()
        }
    }
}

fun browserCloseOptions(context: Context): Bundle? {
    return if (animationsOn(context)) {
        ActivityOptions.makeCustomAnimation(
            context,
            R.anim.browser_close_enter,
            R.anim.browser_close_exit,
        ).toBundle()
    } else {
        ActivityOptions.makeCustomAnimation(
            context,
            R.anim.browser_fade_in,
            R.anim.browser_fade_out,
        ).toBundle()
    }
}
