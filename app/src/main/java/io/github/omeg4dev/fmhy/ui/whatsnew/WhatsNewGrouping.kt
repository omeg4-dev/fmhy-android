package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.ui.common.DateFormatters

enum class WhatsNewTab(val title: String) {
    ADDED("Added"),
    UPDATED("Updated"),
    REMOVED("Removed");

    fun kinds(): Set<ChangeKind> = when (this) {
        ADDED -> setOf(ChangeKind.ADDED)
        UPDATED -> setOf(
            ChangeKind.EDITED,
            ChangeKind.STARRED,
            ChangeKind.UNSTARRED,
            ChangeKind.URL_CHANGED,
            ChangeKind.RENAMED,
            ChangeKind.MOVED,
        )
        REMOVED -> setOf(ChangeKind.REMOVED)
    }
}

data class DayGroup(
    val dayHeader: String,
    val items: List<ChangeItem>,
)

object WhatsNewGrouping {
    fun tabKinds(tab: WhatsNewTab): Set<ChangeKind> = tab.kinds()

    fun filterChanges(
        changes: List<ChangeItem>,
        tab: WhatsNewTab,
    ): List<ChangeItem> {
        val kinds = tabKinds(tab)
        return changes.filter { it.kind in kinds }
    }

    fun groupByDay(
        items: List<ChangeItem>,
        nowSec: Long = System.currentTimeMillis() / 1000,
    ): List<DayGroup> {
        if (items.isEmpty()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<ChangeItem>>()
        for (item in items) {
            val header = DateFormatters.formatDayHeader(item.ts, nowSec)
            groups.getOrPut(header) { mutableListOf() }.add(item)
        }
        return groups.map { (header, groupItems) -> DayGroup(header, groupItems) }
    }

    fun updateLine(change: ChangeItem): String? {
        return when (change.kind) {
            ChangeKind.STARRED -> "★ Starred"
            ChangeKind.UNSTARRED -> "Unstarred"
            ChangeKind.URL_CHANGED -> {
                val host = extractHost(change.url)
                if (host.isNotEmpty()) "New link: $host" else "New link"
            }
            ChangeKind.EDITED -> "Description changed"
            ChangeKind.RENAMED -> "Renamed"
            ChangeKind.MOVED -> "Moved"
            ChangeKind.ADDED -> null
            ChangeKind.REMOVED -> null
        }
    }

    fun extractHost(url: String): String {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val uri = java.net.URI(cleanUrl)
            uri.host?.removePrefix("www.").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
