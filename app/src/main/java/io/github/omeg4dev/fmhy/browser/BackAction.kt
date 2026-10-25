package io.github.omeg4dev.fmhy.browser

/** What system Back does in the browser, in priority order (unit-tested in BackActionTest). */
enum class BackAction { EXIT_FULLSCREEN, CLOSE_FIND, CLOSE_EDIT, WEB_BACK, CLOSE_BLANK_TAB, RETURN_TO_FMHY }

fun backAction(
    fullscreen: Boolean,
    findMode: Boolean,
    editMode: Boolean,
    hasTab: Boolean,
    canGoBack: Boolean,
    isBlankTab: Boolean,
    tabCount: Int,
): BackAction = when {
    fullscreen -> BackAction.EXIT_FULLSCREEN
    findMode -> BackAction.CLOSE_FIND
    editMode -> BackAction.CLOSE_EDIT
    hasTab && canGoBack -> BackAction.WEB_BACK
    // Back on an empty new tab returns to the tab you came from instead of leaving the browser.
    hasTab && isBlankTab && tabCount > 1 -> BackAction.CLOSE_BLANK_TAB
    else -> BackAction.RETURN_TO_FMHY
}
