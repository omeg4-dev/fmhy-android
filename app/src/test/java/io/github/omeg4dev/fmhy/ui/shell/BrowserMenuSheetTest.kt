package io.github.omeg4dev.fmhy.ui.shell

import io.github.omeg4dev.fmhy.browser.TabState
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMenuSheetTest {
    private fun tab(id: Long, active: Long) = TabState(id = id, url = "https://x.org/$id", title = "t$id", lastActiveTime = active)

    @Test
    fun menuTabsNewestFirstCappedAtFive() {
        val tabs = (1L..7L).map { tab(it, active = it * 10) }
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), menuTabs(tabs).map { it.id })
    }

    @Test
    fun subtitle() {
        assertEquals("No tabs open", menuSubtitle(0))
        assertEquals("1 tab", menuSubtitle(1))
        assertEquals("3 tabs", menuSubtitle(3))
    }
}
