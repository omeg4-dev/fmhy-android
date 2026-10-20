package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLinkQueueTest {

    @Test
    fun singleLink_beforeRestore_isDrainedWithSelectTrue() {
        val queue = BrowserLinkQueue()
        assertFalse(queue.isRestored.get())

        val opened = mutableListOf<Pair<String, Boolean>>()
        queue.offerOrExecute(QueuedLink("https://example.com/1")) { link, select ->
            opened.add(link.url to select)
        }

        assertTrue(opened.isEmpty())
        assertTrue(queue.hasPendingLinks())

        queue.isRestored.set(true)
        queue.drain { link, select ->
            opened.add(link.url to select)
        }

        assertEquals(listOf("https://example.com/1" to true), opened)
        assertFalse(queue.hasPendingLinks())
    }

    @Test
    fun twelveLinks_beforeRestore_drainedInOrderWithLastSelected() {
        val queue = BrowserLinkQueue()

        for (i in 1..12) {
            queue.offerOrExecute(QueuedLink("https://example.com/$i")) { _, _ -> }
        }

        assertEquals(12, queue.pendingCount())

        val opened = mutableListOf<Pair<String, Boolean>>()
        queue.isRestored.set(true)
        queue.drain { link, select ->
            opened.add(link.url to select)
        }

        assertEquals(12, opened.size)
        for (i in 1..11) {
            assertEquals("https://example.com/$i", opened[i - 1].first)
            assertFalse("Expected link $i to have select=false", opened[i - 1].second)
        }
        assertEquals("https://example.com/12", opened[11].first)
        assertTrue("Expected 12th link to have select=true", opened[11].second)
    }

    @Test
    fun link_afterRestore_executesImmediatelyWithSelectTrue() {
        val queue = BrowserLinkQueue()
        queue.isRestored.set(true)

        val opened = mutableListOf<Pair<String, Boolean>>()
        queue.offerOrExecute(QueuedLink("https://example.com/immediate")) { link, select ->
            opened.add(link.url to select)
        }

        assertEquals(listOf("https://example.com/immediate" to true), opened)
        assertFalse(queue.hasPendingLinks())
    }

    @Test
    fun multipleLinks_afterRestore_eachExecutesImmediatelyWithSelectTrue() {
        val queue = BrowserLinkQueue()
        queue.isRestored.set(true)

        val opened = mutableListOf<Pair<String, Boolean>>()
        for (i in 1..5) {
            queue.offerOrExecute(QueuedLink("https://example.com/$i")) { link, select ->
                opened.add(link.url to select)
            }
        }

        assertEquals(5, opened.size)
        for (i in 1..5) {
            assertEquals("https://example.com/$i", opened[i - 1].first)
            assertTrue("Expected link $i to have select=true when arriving after restore", opened[i - 1].second)
        }
    }
}
