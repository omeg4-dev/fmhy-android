package io.github.omeg4dev.fmhy.browser

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class QueuedLink(
    val url: String,
    val entryId: String? = null,
    val newTab: Boolean = true,
)

/**
 * Ensures incoming URLs arriving during Activity startup or rapid onNewIntent calls
 * are queued and opened in order once tab restoration completes.
 */
class BrowserLinkQueue {
    val isRestored = AtomicBoolean(false)
    private val queue = ConcurrentLinkedQueue<QueuedLink>()
    /** Guards the check-then-add of [offerOrExecute] against the restore flip + drain in [completeRestore]. */
    private val lock = Any()

    fun enqueue(link: QueuedLink) {
        queue.add(link)
    }

    fun hasPendingLinks(): Boolean = !queue.isEmpty()

    fun pendingCount(): Int = queue.size

    private fun takeAll(): List<QueuedLink> {
        val links = mutableListOf<QueuedLink>()
        while (true) {
            val item = queue.poll() ?: break
            links.add(item)
        }
        return links
    }

    private fun open(links: List<QueuedLink>, onOpen: (link: QueuedLink, select: Boolean) -> Unit) {
        for (i in links.indices) {
            onOpen(links[i], i == links.lastIndex)
        }
    }

    /**
     * Drains all queued links in order.
     * The last link is given [select] = true; preceding links have [select] = false.
     */
    fun drain(onOpen: (link: QueuedLink, select: Boolean) -> Unit) {
        open(synchronized(lock) { takeAll() }, onOpen)
    }

    /**
     * Marks restore as done and drains in one atomic step: a link offered concurrently either lands in the
     * drained batch or sees [isRestored] = true and executes itself, never gets lost in between.
     */
    fun completeRestore(onOpen: (link: QueuedLink, select: Boolean) -> Unit) {
        val links = synchronized(lock) {
            isRestored.set(true)
            takeAll()
        }
        open(links, onOpen)
    }

    /**
     * Called when an intent arrives via onCreate or onNewIntent.
     * If tab restore is already complete, executes immediately with select = true.
     * Otherwise enqueues the link to be drained in order once restore completes.
     */
    fun offerOrExecute(link: QueuedLink, onOpen: (link: QueuedLink, select: Boolean) -> Unit) {
        val runNow = synchronized(lock) {
            if (isRestored.get()) true else { queue.add(link); false }
        }
        if (runNow) onOpen(link, true)
    }
}

/**
 * The browser activity may only finish because it has no tabs once restore is done and nothing is queued:
 * before that the tab list is empty simply because restore has not run yet.
 */
fun shouldFinishWhenEmpty(restored: Boolean, tabCount: Int, currentTabId: Long?, pendingLinks: Boolean): Boolean =
    restored && tabCount == 0 && currentTabId == null && !pendingLinks
