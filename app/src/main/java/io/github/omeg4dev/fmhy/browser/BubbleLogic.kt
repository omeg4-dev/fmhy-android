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
}
