package io.github.omeg4dev.fmhy.browser

data class StatusPopupPlacement(val x: Int, val y: Int, val above: Boolean, val caretX: Int)

/**
 * Where the status popup goes: under the icon (top bar) or above it (bottom bar), flipped when it
 * doesn't fit; x kept [marginPx] inside the window; caretX = icon centre relative to the popup's left edge.
 */
fun statusPopupPlacement(
    anchorLeft: Int, anchorTop: Int, anchorRight: Int, anchorBottom: Int,
    windowWidth: Int, windowHeight: Int,
    popupWidth: Int, popupHeight: Int,
    preferAbove: Boolean, marginPx: Int, gapPx: Int, caretHalfPx: Int,
): StatusPopupPlacement {
    val anchorCenterX = (anchorLeft + anchorRight) / 2
    val x = (anchorCenterX - popupWidth / 2).coerceIn(
        marginPx,
        maxOf(marginPx, windowWidth - marginPx - popupWidth),
    )

    val below = anchorBottom + gapPx
    val above = anchorTop - gapPx - popupHeight
    val fitsBelow = below + popupHeight <= windowHeight - marginPx
    val fitsAbove = above >= marginPx
    val placeAbove = if (preferAbove) fitsAbove || !fitsBelow else !fitsBelow && fitsAbove

    val rawY = if (placeAbove) above else below
    val y = if (windowHeight - 2 * marginPx >= popupHeight) {
        rawY.coerceIn(marginPx, windowHeight - marginPx - popupHeight)
    } else {
        marginPx
    }

    val caretRange = caretHalfPx + 24..popupWidth - caretHalfPx - 24
    val caretX = if (caretRange.first > caretRange.last) {
        popupWidth / 2
    } else {
        (anchorCenterX - x).coerceIn(caretRange.first, caretRange.last)
    }

    return StatusPopupPlacement(x = x, y = y, above = placeAbove, caretX = caretX)
}
