package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedNoticesTest {

    private fun dummyEntry(
        kind: BlockedKind,
        host: String? = "example.com",
        targetUrl: String = "https://example.com/test",
        actionLabel: String = "Open",
        actionable: Boolean = true
    ): BlockedEntry {
        return BlockedEntry(
            kind = kind,
            host = host,
            targetUrl = targetUrl,
            actionLabel = actionLabel,
            onAction = {},
            onAlwaysAllow = if (kind != BlockedKind.APP_LINK) ({}) else null,
            actionable = actionable
        )
    }

    @Test
    fun testBlockedBarStateAddAndTitles() {
        val state = BlockedBarState()
        assertEquals("", state.title())
        assertFalse(state.visible)
        assertTrue(state.entries.isEmpty())

        // 1 pop-up
        val entry1 = dummyEntry(BlockedKind.POPUP, host = "example.com")
        val added1 = state.add(entry1)
        assertTrue(added1)
        assertTrue(state.visible)
        assertEquals(1, state.entries.size)
        assertEquals("Blocked pop-up to example.com", state.title())

        // Null host pop-up
        val nullHostPopupState = BlockedBarState()
        nullHostPopupState.add(dummyEntry(BlockedKind.POPUP, host = null))
        assertEquals("Blocked a pop-up", nullHostPopupState.title())

        // 1 redirect
        val redirectState = BlockedBarState()
        redirectState.add(dummyEntry(BlockedKind.REDIRECT, host = "example.com"))
        assertEquals("Blocked redirect to example.com", redirectState.title())

        val nullHostRedirectState = BlockedBarState()
        nullHostRedirectState.add(dummyEntry(BlockedKind.REDIRECT, host = null))
        assertEquals("Blocked a redirect", nullHostRedirectState.title())

        // 1 app link
        val appLinkState = BlockedBarState()
        appLinkState.add(dummyEntry(BlockedKind.APP_LINK, host = null))
        assertEquals("Blocked a link that opens an app", appLinkState.title())

        // Back to state with entry1: add 2nd and 3rd entry
        val entry2 = dummyEntry(BlockedKind.REDIRECT, host = "another.com")
        val added2 = state.add(entry2)
        assertFalse(added2)
        assertEquals("2 blocked on this page", state.title())

        val entry3 = dummyEntry(BlockedKind.APP_LINK, host = null)
        val added3 = state.add(entry3)
        assertFalse(added3)
        assertEquals("3 blocked on this page", state.title())
    }

    @Test
    fun testBlockedBarStateClearAndRemove() {
        val state = BlockedBarState()
        val entry1 = dummyEntry(BlockedKind.POPUP, host = "a.com")
        val entry2 = dummyEntry(BlockedKind.REDIRECT, host = "b.com")
        val entry3 = dummyEntry(BlockedKind.APP_LINK, host = null)

        state.add(entry1)
        state.add(entry2)
        state.add(entry3)
        assertEquals(3, state.entries.size)

        // remove of one of three leaves 2 and title "2 blocked on this page"
        state.remove(entry2)
        assertEquals(2, state.entries.size)
        assertTrue(state.visible)
        assertEquals("2 blocked on this page", state.title())

        // clear() hides (visible == false, entries empty)
        state.clear()
        assertFalse(state.visible)
        assertTrue(state.entries.isEmpty())
        assertEquals("", state.title())

        // next add returns true again
        val addedAgain = state.add(entry1)
        assertTrue(addedAgain)
        assertTrue(state.visible)
        assertEquals("Blocked pop-up to a.com", state.title())

        // remove of the last entry hides the bar
        state.remove(entry1)
        assertFalse(state.visible)
        assertTrue(state.entries.isEmpty())
        assertEquals("", state.title())
    }

    @Test
    fun testBackgroundTabMerge() {
        val merge = BackgroundTabMerge(windowMs = 2000L)

        // one tab -> Single(id)
        val notice1 = merge.record(101L, 0L)
        assertEquals(TabNotice.Single(101L), notice1)

        // reset and test 3 tabs at t=0, 500, 1000 -> Single, Merged(2), Merged(3)
        merge.reset()
        val t0 = merge.record(1L, 0L)
        val t500 = merge.record(2L, 500L)
        val t1000 = merge.record(3L, 1000L)
        assertEquals(TabNotice.Single(1L), t0)
        assertEquals(TabNotice.Merged(2), t500)
        assertEquals(TabNotice.Merged(3), t1000)

        // a tab at t=1000 then t=3500 -> both Single
        merge.reset()
        val first = merge.record(10L, 1000L)
        val second = merge.record(11L, 3500L)
        assertEquals(TabNotice.Single(10L), first)
        assertEquals(TabNotice.Single(11L), second)

        // boundary: exactly 2000 ms gap -> new run (Single), 1999 ms -> merged
        merge.reset()
        merge.record(20L, 1000L)
        val boundaryExact = merge.record(21L, 3000L) // gap = 2000 ms -> gap < 2000 is false
        assertEquals(TabNotice.Single(21L), boundaryExact)

        // 1999 ms gap -> merged
        val boundaryWithin = merge.record(22L, 4999L) // gap = 1999 ms -> gap < 2000 is true
        assertEquals(TabNotice.Merged(2), boundaryWithin)
    }
}
