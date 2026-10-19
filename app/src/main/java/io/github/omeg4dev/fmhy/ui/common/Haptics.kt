package io.github.omeg4dev.fmhy.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

val LocalHapticsEnabled = staticCompositionLocalOf { true }

interface AppHaptics {
    fun tick()
    fun click()
    fun longPress()
    fun confirm()
}

private object NoOpHaptics : AppHaptics {
    override fun tick() {}
    override fun click() {}
    override fun longPress() {}
    override fun confirm() {}
}

@Composable
fun rememberHaptics(): AppHaptics {
    val enabled = LocalHapticsEnabled.current
    val view = LocalView.current
    return remember(enabled, view) {
        if (!enabled) {
            NoOpHaptics
        } else {
            object : AppHaptics {
                override fun tick() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }

                override fun click() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }

                override fun longPress() {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }

                override fun confirm() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
        }
    }
}
