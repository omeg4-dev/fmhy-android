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

    fun enqueue(link: QueuedLink) {
        queue.add(link)
    }

    fun hasPendingLinks(): Boolean = !queue.isEmpty()

    fun pendingCount(): Int = queue.size

    /**
     * Drains all queued links in order.
     * The last link is given [select] = true; preceding links have [select] = false.
     */
    fun drain(onOpen: (link: QueuedLink, select: Boolean) -> Unit) {
        val links = mutableListOf<QueuedLink>()
        while (true) {
            val item = queue.poll() ?: break
            links.add(item)
        }
        for (i in links.indices) {
            val isLast = (i == links.lastIndex)
            onOpen(links[i], isLast)
        }
    }

    /**
     * Called when an intent arrives via onCreate or onNewIntent.
     * If tab restore is already complete, executes immediately with select = true.
     * Otherwise enqueues the link to be drained in order once restore completes.
     */
    fun offerOrExecute(link: QueuedLink, onOpen: (link: QueuedLink, select: Boolean) -> Unit) {
        if (isRestored.get()) {
            onOpen(link, true)
        } else {
            queue.add(link)
            if (isRestored.get()) {
                drain(onOpen)
            }
        }
    }
}
