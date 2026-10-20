package io.github.omeg4dev.fmhy.browser

import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabLruTest {

    private fun createDummyWebView(): WebView {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(WebView::class.java) as WebView
    }

    private fun makeRecord(id: Long, lastActiveTime: Long, hasWebView: Boolean): TabRecord {
        val r = TabRecord(
            id = id,
            url = "https://example.com/$id",
            title = "Tab $id",
            lastActiveTime = lastActiveTime,
        )
        if (hasWebView) {
            r.webView = createDummyWebView()
        }
        return r
    }

    @Test
    fun selectLruVictim_picksOldestLiveTab() {
        val r1 = makeRecord(1L, lastActiveTime = 100L, hasWebView = true)
        val r2 = makeRecord(2L, lastActiveTime = 50L, hasWebView = true)
        val r3 = makeRecord(3L, lastActiveTime = 200L, hasWebView = true)

        val victim = selectLruVictim(listOf(r1, r2, r3), currentTabId = 3L, previousTabId = 1L)
        assertEquals(2L, victim?.id)
    }

    @Test
    fun selectLruVictim_neverReturnsCurrentTab() {
        val r1 = makeRecord(1L, lastActiveTime = 10L, hasWebView = true) // oldest, but current
        val r2 = makeRecord(2L, lastActiveTime = 50L, hasWebView = true)
        val r3 = makeRecord(3L, lastActiveTime = 100L, hasWebView = true)

        val victim = selectLruVictim(listOf(r1, r2, r3), currentTabId = 1L, previousTabId = null)
        assertEquals(2L, victim?.id)
    }

    @Test
    fun selectLruVictim_neverReturnsPreviousTab() {
        val r1 = makeRecord(1L, lastActiveTime = 10L, hasWebView = true) // oldest, but previous
        val r2 = makeRecord(2L, lastActiveTime = 50L, hasWebView = true)
        val r3 = makeRecord(3L, lastActiveTime = 100L, hasWebView = true)

        val victim = selectLruVictim(listOf(r1, r2, r3), currentTabId = 3L, previousTabId = 1L)
        assertEquals(2L, victim?.id)
    }

    @Test
    fun selectLruVictim_neverReturnsCandidateTab() {
        val r1 = makeRecord(1L, lastActiveTime = 10L, hasWebView = true) // candidate
        val r2 = makeRecord(2L, lastActiveTime = 50L, hasWebView = true)

        val victim = selectLruVictim(listOf(r1, r2), currentTabId = null, previousTabId = null, candidateTabId = 1L)
        assertEquals(2L, victim?.id)
    }

    @Test
    fun selectLruVictim_ignoresTabsWithoutLiveWebView() {
        val r1 = makeRecord(1L, lastActiveTime = 10L, hasWebView = false) // oldest, but no webView
        val r2 = makeRecord(2L, lastActiveTime = 50L, hasWebView = true)

        val victim = selectLruVictim(listOf(r1, r2), currentTabId = null, previousTabId = null)
        assertEquals(2L, victim?.id)
    }

    @Test
    fun selectLruVictim_returnsNullWhenAllLiveTabsAreCurrentOrPrevious() {
        val r1 = makeRecord(1L, lastActiveTime = 10L, hasWebView = true) // current
        val r2 = makeRecord(2L, lastActiveTime = 20L, hasWebView = true) // previous

        val victim = selectLruVictim(listOf(r1, r2), currentTabId = 1L, previousTabId = 2L)
        assertNull(victim)
    }
}
