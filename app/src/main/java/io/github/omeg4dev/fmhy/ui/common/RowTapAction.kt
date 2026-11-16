package io.github.omeg4dev.fmhy.ui.common

enum class RowAction { OPEN_SITE, OPEN_MENU, SHOW_IN_SECTION }

data class RowGestures(val onTap: RowAction, val onHold: RowAction)

fun rowGestures(swapTapHold: Boolean, siteAction: RowAction = RowAction.OPEN_SITE): RowGestures =
    if (swapTapHold) RowGestures(RowAction.OPEN_MENU, siteAction) else RowGestures(siteAction, RowAction.OPEN_MENU)
