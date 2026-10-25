package io.github.omeg4dev.fmhy.ui.common

enum class RowAction { OPEN_SITE, OPEN_MENU }

data class RowGestures(val onTap: RowAction, val onHold: RowAction)

fun rowGestures(swapTapHold: Boolean): RowGestures =
    if (swapTapHold) RowGestures(RowAction.OPEN_MENU, RowAction.OPEN_SITE) else RowGestures(RowAction.OPEN_SITE, RowAction.OPEN_MENU)
