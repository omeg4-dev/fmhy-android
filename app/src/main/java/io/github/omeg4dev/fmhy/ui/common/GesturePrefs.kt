package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

data class GesturePrefs(
    val swipeToBookmark: Boolean = false,
    val swapTapHold: Boolean = false,
)

val LocalGesturePrefs = staticCompositionLocalOf { GesturePrefs() }
