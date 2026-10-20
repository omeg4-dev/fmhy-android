package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueBrowsingRowsTest {
    private fun tab(id: Long, url: String = "https://e$id.com", active: Long, priv: Boolean = false) =
        TabState(id = id, url = url, title = "t$id", isPrivate = priv, lastActiveTime = active)

    @Test
    fun mostRecentFirst_limitedToThree() {
        val rows = continueBrowsingRows(listOf(tab(1, active = 10), tab(2, active = 40), tab(3, active = 30), tab(4, active = 20)))
        assertEquals(listOf(2L, 3L, 4L), rows.map { it.id })
    }

    @Test
    fun skipsPrivateAndBlankTabs() {
        val rows = continueBrowsingRows(listOf(tab(1, active = 1, priv = true), tab(2, url = "about:blank", active = 2), tab(3, active = 3)))
        assertEquals(listOf(3L), rows.map { it.id })
        assertTrue(continueBrowsingRows(emptyList()).isEmpty())
    }
}
