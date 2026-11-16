package io.github.omeg4dev.fmhy.browser

import androidx.compose.runtime.mutableStateListOf

enum class BlockedKind { POPUP, REDIRECT, APP_LINK }

class BlockedEntry(
    val kind: BlockedKind,
    val host: String?,                 // null when unknown
    val targetUrl: String,
    val actionLabel: String,           // "Open" / "Allow" / "Open" (app link)
    val onAction: () -> Unit,
    val onAlwaysAllow: (() -> Unit)?,  // null for APP_LINK
    val actionable: Boolean = true,
)

fun singleEntryTitle(entry: BlockedEntry): String {
    return when (entry.kind) {
        BlockedKind.POPUP -> if (entry.host != null) "Blocked pop-up to ${entry.host}" else "Blocked a pop-up"
        BlockedKind.REDIRECT -> if (entry.host != null) "Blocked redirect to ${entry.host}" else "Blocked a redirect"
        BlockedKind.APP_LINK -> "Blocked a link that opens an app"
    }
}

class BlockedBarState {
    private val _entries = mutableStateListOf<BlockedEntry>()
    val entries: List<BlockedEntry> get() = _entries
    val visible: Boolean get() = _entries.isNotEmpty()

    /** Adds an entry; returns true iff the bar was hidden before (caller vibrates only then). */
    fun add(entry: BlockedEntry): Boolean {
        val wasHidden = _entries.isEmpty()
        _entries.add(entry)
        return wasHidden
    }

    fun remove(entry: BlockedEntry) {
        _entries.remove(entry)
    }

    fun clear() {
        _entries.clear()
    }

    fun title(): String {
        return when (_entries.size) {
            0 -> ""
            1 -> singleEntryTitle(_entries[0])
            else -> "${_entries.size} blocked on this page"
        }
    }
}

sealed interface TabNotice {
    data class Single(val tabId: Long) : TabNotice            // text "Opened in a new tab", action "Switch"
    data class Merged(val count: Int) : TabNotice             // text "<count> tabs opened in the background", action "Show tabs"
}

class BackgroundTabMerge(private val windowMs: Long = 2000L) {
    private var lastTabMs: Long = -1L
    private var currentRunCount: Int = 0

    /** Record one background tab opened at [nowMs]. Returns the notice to show NOW. */
    fun record(tabId: Long, nowMs: Long): TabNotice {
        return if (lastTabMs >= 0L && (nowMs - lastTabMs < windowMs)) {
            currentRunCount++
            lastTabMs = nowMs
            TabNotice.Merged(currentRunCount)
        } else {
            currentRunCount = 1
            lastTabMs = nowMs
            TabNotice.Single(tabId)
        }
    }

    fun reset() {
        lastTabMs = -1L
        currentRunCount = 0
    }
}
