package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.BrowserButtonStyle
import io.github.omeg4dev.fmhy.data.BubbleSide

/** Pure logic of the floating browser bubble (unit-tested in BubbleLogicTest). */
object BubbleLogic {
    /** The bubble shows iff tabs are open, the style is floating and it was not hidden from its menu. */
    fun isVisible(tabCount: Int, style: BrowserButtonStyle, hidden: Boolean): Boolean =
        tabCount > 0 && style == BrowserButtonStyle.FLOATING && !hidden

    fun badgeText(count: Int): String = if (count > 9) "9+" else count.toString()

    /** Nearest horizontal edge for a bubble whose left edge is at [x]. */
    fun nearestSide(x: Float, bubbleSize: Float, containerWidth: Float): BubbleSide =
        if (x + bubbleSize / 2f < containerWidth / 2f) BubbleSide.LEFT else BubbleSide.RIGHT

    fun edgeX(side: BubbleSide, bubbleSize: Float, containerWidth: Float, margin: Float): Float =
        if (side == BubbleSide.LEFT) margin else containerWidth - bubbleSize - margin

    fun clampY(y: Float, minY: Float, maxY: Float): Float =
        if (maxY <= minY) minY else y.coerceIn(minY, maxY)

    fun yToFraction(y: Float, minY: Float, maxY: Float): Float =
        if (maxY <= minY) 0f else ((y - minY) / (maxY - minY)).coerceIn(0f, 1f)

    fun fractionToY(fraction: Float, minY: Float, maxY: Float): Float =
        if (maxY <= minY) minY else minY + fraction.coerceIn(0f, 1f) * (maxY - minY)

    /**
     * The back-to-FMHY bubble shows unless the user hid it or something else owns the screen
     * (fullscreen video, picture-in-picture, find / address edit mode, the keyboard).
     */
    fun backBubbleVisible(
        hidden: Boolean,
        fullscreen: Boolean,
        pip: Boolean,
        ime: Boolean,
        editMode: Boolean,
        findMode: Boolean = false,
    ): Boolean = !hidden && !fullscreen && !pip && !ime && !editMode && !findMode

    /**
     * Allowed top of the back bubble, in container px: below the status bar (and the top toolbar when it
     * is at the top), never above it.
     */
    fun backBubbleMinY(statusBarPx: Float, topBarPx: Float, marginPx: Float): Float = statusBarPx + topBarPx + marginPx

    /** Allowed top-most position of the bubble's bottom edge: above the nav bar and a bottom toolbar. */
    fun backBubbleMaxY(containerHeight: Float, navBarPx: Float, bottomBarPx: Float, bubbleSize: Float, marginPx: Float): Float =
        containerHeight - navBarPx - bottomBarPx - bubbleSize - marginPx

    /** 0 = bars fully shown, 1 = fully hidden, from the toolbar translation. */
    fun barHiddenFraction(translation: Float, barHeight: Float): Float =
        if (barHeight <= 0f) 0f else (kotlin.math.abs(translation) / barHeight).coerceIn(0f, 1f)
}
