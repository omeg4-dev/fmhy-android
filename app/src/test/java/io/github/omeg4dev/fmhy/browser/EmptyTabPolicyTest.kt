package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyTabPolicyTest {

    // ── isStartUrl ────────────────────────────────────────────────────────────

    @Test
    fun `isStartUrl returns true for empty string`() {
        assertTrue(EmptyTabPolicy.isStartUrl(""))
    }

    @Test
    fun `isStartUrl returns true for blank string`() {
        assertTrue(EmptyTabPolicy.isStartUrl("   "))
    }

    @Test
    fun `isStartUrl returns true for about-blank`() {
        assertTrue(EmptyTabPolicy.isStartUrl("about:blank"))
    }

    @Test
    fun `isStartUrl returns false for real url`() {
        assertFalse(EmptyTabPolicy.isStartUrl("https://example.com"))
    }

    // ── isEmpty(url, hadSite) ─────────────────────────────────────────────────

    @Test
    fun `isEmpty returns true for blank url without hadSite`() {
        assertTrue(EmptyTabPolicy.isEmpty("about:blank", hadSite = false))
    }

    @Test
    fun `isEmpty returns false for blank url when hadSite is true`() {
        assertFalse(EmptyTabPolicy.isEmpty("about:blank", hadSite = true))
    }

    @Test
    fun `isEmpty returns false for real url even without hadSite`() {
        assertFalse(EmptyTabPolicy.isEmpty("https://example.com", hadSite = false))
    }

    // ── isEmpty(TabState) ─────────────────────────────────────────────────────

    private fun tab(
        id: Long = 1L,
        url: String = "about:blank",
        hadSite: Boolean = false,
        isPrivate: Boolean = false,
    ) = TabState(id = id, url = url, title = "Tab", hadSite = hadSite, isPrivate = isPrivate)

    @Test
    fun `isEmpty TabState true when blank and no hadSite`() {
        assertTrue(EmptyTabPolicy.isEmpty(tab(url = "about:blank", hadSite = false)))
    }

    @Test
    fun `isEmpty TabState false when blank but hadSite is true`() {
        assertFalse(EmptyTabPolicy.isEmpty(tab(url = "about:blank", hadSite = true)))
    }

    @Test
    fun `isEmpty TabState false when real url`() {
        assertFalse(EmptyTabPolicy.isEmpty(tab(url = "https://fmhy.net", hadSite = false)))
    }

    // ── toCloseOnLeave ────────────────────────────────────────────────────────

    @Test
    fun `toCloseOnLeave returns empty tab ids excluding keepId`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false),  // empty
            tab(id = 2L, url = "https://example.com", hadSite = true),  // not empty
            tab(id = 3L, url = "about:blank", hadSite = false),  // empty
        )
        val result = EmptyTabPolicy.toCloseOnLeave(tabs, keepId = 1L)
        assertEquals(listOf(3L), result)
    }

    @Test
    fun `toCloseOnLeave with null keepId returns all empty tab ids`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false, isPrivate = false),
            tab(id = 2L, url = "about:blank", hadSite = false, isPrivate = true),
            tab(id = 3L, url = "https://example.com", hadSite = true),
        )
        val result = EmptyTabPolicy.toCloseOnLeave(tabs, keepId = null)
        assertEquals(listOf(1L, 2L), result)
    }

    @Test
    fun `toCloseOnLeave returns empty list when no empty tabs`() {
        val tabs = listOf(
            tab(id = 1L, url = "https://example.com", hadSite = true),
        )
        assertTrue(EmptyTabPolicy.toCloseOnLeave(tabs, keepId = null).isEmpty())
    }

    @Test
    fun `toCloseOnLeave includes empty tabs from both privacies`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false, isPrivate = false),
            tab(id = 2L, url = "about:blank", hadSite = false, isPrivate = true),
        )
        val result = EmptyTabPolicy.toCloseOnLeave(tabs, keepId = 99L)
        assertEquals(listOf(1L, 2L), result)
    }

    // ── reusable ──────────────────────────────────────────────────────────────

    @Test
    fun `reusable prefers currentId when it is empty and matches privacy`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false, isPrivate = false),
            tab(id = 2L, url = "about:blank", hadSite = false, isPrivate = false),
        )
        val result = EmptyTabPolicy.reusable(tabs, isPrivate = false, currentId = 2L)
        assertEquals(2L, result?.id)
    }

    @Test
    fun `reusable falls back to first matching empty tab when current is not empty`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false, isPrivate = false),
            tab(id = 2L, url = "https://example.com", hadSite = true, isPrivate = false),
        )
        val result = EmptyTabPolicy.reusable(tabs, isPrivate = false, currentId = 2L)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `reusable returns null when no empty tabs match privacy`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false, isPrivate = true),
        )
        val result = EmptyTabPolicy.reusable(tabs, isPrivate = false, currentId = null)
        assertNull(result)
    }

    @Test
    fun `reusable does not match current tab with wrong privacy`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false, isPrivate = true),
        )
        // currentId=1 is empty but private, we want non-private
        val result = EmptyTabPolicy.reusable(tabs, isPrivate = false, currentId = 1L)
        assertNull(result)
    }

    @Test
    fun `reusable returns null when all empty tabs are on-screen (keepId covers all)`() {
        val tabs = listOf(
            tab(id = 1L, url = "https://real.example.com", hadSite = true, isPrivate = false),
        )
        val result = EmptyTabPolicy.reusable(tabs, isPrivate = false, currentId = null)
        assertNull(result)
    }

    // ── restorable ────────────────────────────────────────────────────────────

    @Test
    fun `restorable excludes empty (about-blank) tabs`() {
        val tabs = listOf(
            tab(id = 1L, url = "about:blank", hadSite = false, isPrivate = false),
            tab(id = 2L, url = "https://example.com", hadSite = true, isPrivate = false),
        )
        val result = EmptyTabPolicy.restorable(tabs)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `restorable excludes private tabs`() {
        val tabs = listOf(
            tab(id = 1L, url = "https://example.com", hadSite = true, isPrivate = true),
            tab(id = 2L, url = "https://example.com", hadSite = true, isPrivate = false),
        )
        val result = EmptyTabPolicy.restorable(tabs)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `restorable excludes blank url tabs`() {
        val tabs = listOf(
            tab(id = 1L, url = "", hadSite = false, isPrivate = false),
            tab(id = 2L, url = "https://example.com", hadSite = true, isPrivate = false),
        )
        val result = EmptyTabPolicy.restorable(tabs)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `restorable keeps real non-private tabs`() {
        val tabs = listOf(
            tab(id = 1L, url = "https://fmhy.net", hadSite = true, isPrivate = false),
            tab(id = 2L, url = "https://example.com", hadSite = true, isPrivate = false),
        )
        assertEquals(2, EmptyTabPolicy.restorable(tabs).size)
    }
}
