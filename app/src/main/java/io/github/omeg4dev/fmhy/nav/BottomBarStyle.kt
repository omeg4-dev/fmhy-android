package io.github.omeg4dev.fmhy.nav

enum class BottomBarStyle(val label: String) {
    CLASSIC("Classic"),
    ROUNDED_TOP("Rounded top corners"),
    FLOATING("Floating bubble"),
    SELECTED_LABEL_ONLY("Labels only on selected item");

    companion object {
        fun fromName(name: String?): BottomBarStyle {
            if (name.isNullOrEmpty()) return CLASSIC
            return try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                CLASSIC
            }
        }
    }
}

data class BottomBarMetrics(
    val topCornerDp: Int,
    val floating: Boolean,
    val horizontalMarginDp: Int,
    val bottomMarginDp: Int,
    val maxWidthDp: Int?,
    val heightDp: Int,
    val alwaysShowLabel: Boolean,
    val shadowDp: Int,
)

fun BottomBarStyle.metrics(): BottomBarMetrics = when (this) {
    BottomBarStyle.CLASSIC -> BottomBarMetrics(
        topCornerDp = 0,
        floating = false,
        horizontalMarginDp = 0,
        bottomMarginDp = 0,
        maxWidthDp = null,
        heightDp = 80,
        alwaysShowLabel = true,
        shadowDp = 0,
    )
    BottomBarStyle.ROUNDED_TOP -> BottomBarMetrics(
        topCornerDp = 24,
        floating = false,
        horizontalMarginDp = 0,
        bottomMarginDp = 0,
        maxWidthDp = null,
        heightDp = 80,
        alwaysShowLabel = true,
        shadowDp = 0,
    )
    BottomBarStyle.FLOATING -> BottomBarMetrics(
        topCornerDp = 32,
        floating = true,
        horizontalMarginDp = 16,
        bottomMarginDp = 8,
        maxWidthDp = 480,
        heightDp = 72,
        alwaysShowLabel = true,
        shadowDp = 6,
    )
    BottomBarStyle.SELECTED_LABEL_ONLY -> BottomBarMetrics(
        topCornerDp = 0,
        floating = false,
        horizontalMarginDp = 0,
        bottomMarginDp = 0,
        maxWidthDp = null,
        heightDp = 64,
        alwaysShowLabel = false,
        shadowDp = 0,
    )
}

fun BottomBarMetrics.contentPaddingDp(): Int =
    heightDp + bottomMarginDp + (if (floating) 8 else 0)
