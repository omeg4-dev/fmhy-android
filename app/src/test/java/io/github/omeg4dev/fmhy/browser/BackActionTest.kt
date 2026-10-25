package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BackActionTest {
    @Test
    fun pageWithHistoryReturnsWebBack() {
        val action = backAction(
            fullscreen = false,
            findMode = false,
            editMode = false,
            hasTab = true,
            canGoBack = true,
            isBlankTab = false,
            tabCount = 1,
        )
        assertEquals(BackAction.WEB_BACK, action)
    }

    @Test
    fun pageWithoutHistoryReturnsReturnToFmhy() {
        val action = backAction(
            fullscreen = false,
            findMode = false,
            editMode = false,
            hasTab = true,
            canGoBack = false,
            isBlankTab = false,
            tabCount = 1,
        )
        assertEquals(BackAction.RETURN_TO_FMHY, action)
    }

    @Test
    fun blankTabWithMultipleTabsReturnsCloseBlankTab() {
        val action = backAction(
            fullscreen = false,
            findMode = false,
            editMode = false,
            hasTab = true,
            canGoBack = false,
            isBlankTab = true,
            tabCount = 2,
        )
        assertEquals(BackAction.CLOSE_BLANK_TAB, action)
    }

    @Test
    fun blankTabWithSingleTabReturnsReturnToFmhy() {
        val action = backAction(
            fullscreen = false,
            findMode = false,
            editMode = false,
            hasTab = true,
            canGoBack = false,
            isBlankTab = true,
            tabCount = 1,
        )
        assertEquals(BackAction.RETURN_TO_FMHY, action)
    }

    @Test
    fun fullscreenWinsOverEverything() {
        val action = backAction(
            fullscreen = true,
            findMode = true,
            editMode = true,
            hasTab = true,
            canGoBack = true,
            isBlankTab = true,
            tabCount = 5,
        )
        assertEquals(BackAction.EXIT_FULLSCREEN, action)
    }

    @Test
    fun findModeWinsOverWebBack() {
        val action = backAction(
            fullscreen = false,
            findMode = true,
            editMode = false,
            hasTab = true,
            canGoBack = true,
            isBlankTab = false,
            tabCount = 1,
        )
        assertEquals(BackAction.CLOSE_FIND, action)
    }

    @Test
    fun editModeWinsOverWebBack() {
        val action = backAction(
            fullscreen = false,
            findMode = false,
            editMode = true,
            hasTab = true,
            canGoBack = true,
            isBlankTab = false,
            tabCount = 1,
        )
        assertEquals(BackAction.CLOSE_EDIT, action)
    }

    @Test
    fun noTabReturnsReturnToFmhyEvenIfCanGoBackIsTrue() {
        val action = backAction(
            fullscreen = false,
            findMode = false,
            editMode = false,
            hasTab = false,
            canGoBack = true,
            isBlankTab = false,
            tabCount = 0,
        )
        assertEquals(BackAction.RETURN_TO_FMHY, action)
    }

    @Test
    fun findModeWinsOverEditMode() {
        val action = backAction(
            fullscreen = false,
            findMode = true,
            editMode = true,
            hasTab = true,
            canGoBack = true,
            isBlankTab = false,
            tabCount = 1,
        )
        assertEquals(BackAction.CLOSE_FIND, action)
    }

    @Test
    fun blankTabWithHistoryReturnsWebBack() {
        // hasTab && canGoBack takes precedence before blank tab close
        val action = backAction(
            fullscreen = false,
            findMode = false,
            editMode = false,
            hasTab = true,
            canGoBack = true,
            isBlankTab = true,
            tabCount = 2,
        )
        assertEquals(BackAction.WEB_BACK, action)
    }
}
