package io.github.omeg4dev.fmhy.browser

import kotlin.math.roundToInt

/**
 * Calculates the resting top offset (translationY) of the WebView.
 * When the bar is hidden, the WebView sits at 0f (top of screen).
 * When the bar is shown, the WebView is pushed down by the bar's height.
 */
fun pageOffsetFor(barHidden: Boolean, barHeightPx: Float): Float {
    return if (barHidden) 0f else barHeightPx.coerceAtLeast(0f)
}

/**
 * Calculates the scroll compensation delta to apply via [android.view.View.scrollBy]
 * when switching WebView translationY from [oldTy] to [newTy].
 *
 * Screen y of content = translationY - scrollY + docY.
 * Keeping screen y invariant across translation changes requires:
 * newTy - scrollY' = oldTy - scrollY
 * => scrollY' - scrollY = newTy - oldTy.
 *
 * Hiding (H -> 0): compensation is -H.
 * Showing (0 -> H): compensation is +H.
 * No-op (same ty): compensation is 0.
 */
fun compensationFor(oldTy: Float, newTy: Float): Int {
    return (newTy - oldTy).roundToInt()
}

/**
 * Returns true if hiding the top bar is safe without clamping scrollY at 0.
 * To avoid a visible content jump from scroll clamping, hiding is only allowed
 * when webView.scrollY >= H.
 */
fun canHideTopBar(scrollY: Int, barHeightPx: Float): Boolean {
    val h = barHeightPx.roundToInt()
    return h <= 0 || scrollY >= h
}
