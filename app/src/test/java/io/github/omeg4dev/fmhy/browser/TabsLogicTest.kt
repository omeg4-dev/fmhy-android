package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabsLogicTest {
    private fun t(id: Long, url: String = "https://example.com/$id", title: String = "T$id", priv: Boolean = false) =
        TabState(id = id, url = url, title = title, isPrivate = priv)

    @Test fun groupsNormalAndPrivate() {
        val g = TabsLogic.group(listOf(t(1), t(2, priv = true), t(3), t(4, priv = true)))
        assertEquals(listOf(1L, 3L), g.normal.map { it.id })
        assertEquals(listOf(2L, 4L), g.priv.map { it.id })
    }

    @Test fun domainStripsWwwAndHandlesBlank() {
        assertEquals("github.com", TabsLogic.domain("https://www.github.com/gorhill/uBlock"))
        assertEquals("sub.example.org", TabsLogic.domain("http://sub.example.org:8080/x?y#z"))
        assertEquals("10.0.2.2", TabsLogic.domain("http://10.0.2.2:18765/long.html"))
        assertEquals("", TabsLogic.domain("about:blank"))
        assertEquals("", TabsLogic.domain(""))
    }

    @Test fun filterMatchesTitleOrUrl() {
        val tabs = listOf(t(1, "https://a.com", "Alpha"), t(2, "https://beta.org/x", "Other"), t(3, "https://c.net", "Gamma"))
        assertEquals(listOf(1L), TabsLogic.filter(tabs, "alp").map { it.id })
        assertEquals(listOf(2L), TabsLogic.filter(tabs, " BETA ").map { it.id })
        assertEquals(3, TabsLogic.filter(tabs, "  ").size)
        assertEquals(0, TabsLogic.filter(tabs, "zzz").size)
    }

    @Test fun titleText() {
        assertEquals("1 tab", TabsLogic.title(1, 0))
        assertEquals("7 tabs", TabsLogic.title(7, 0))
        assertEquals("7 tabs · 1 private", TabsLogic.title(7, 1))
    }

    @Test fun currentIndexFollowsDisplayOrder() {
        val g = TabsLogic.group(listOf(t(1), t(2, priv = true), t(3)))
        // displayed: 1, 3, 2(private)
        assertEquals(1, TabsLogic.currentIndex(g, 3))
        assertEquals(2, TabsLogic.currentIndex(g, 2))
        assertEquals(-1, TabsLogic.currentIndex(g, 99))
        assertEquals(-1, TabsLogic.currentIndex(g, null))
    }
}
