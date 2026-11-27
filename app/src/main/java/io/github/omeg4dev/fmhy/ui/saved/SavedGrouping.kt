package io.github.omeg4dev.fmhy.ui.saved

import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.SavedSort
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.HistoryItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoryUiItem(
    val history: HistoryItem,
    val liveEntry: Entry?,
    val host: String,
    val timeLabel: String,
)

data class HistoryGroup(
    val dayLabel: String,
    val items: List<HistoryUiItem>,
)

object SavedGrouping {

    fun extractHost(url: String): String {
        return try {
            val withoutScheme = if (url.contains("://")) url.substringAfter("://") else url
            val hostPart = withoutScheme.substringBefore('/').substringBefore('?').substringBefore(':')
            hostPart.removePrefix("www.")
        } catch (_: Exception) {
            url
        }
    }

    fun formatDayMonth(ts: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val millis = if (ts > 100_000_000_000L) ts else ts * 1000L
        val instant = Instant.ofEpochMilli(millis)
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(zoneId)
        return formatter.format(instant)
    }

    fun formatRemovedDate(ts: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return "Removed on ${formatDayMonth(ts, zoneId)}"
    }

    fun formatDayLabel(
        ts: Long,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val millis = if (ts > 100_000_000_000L) ts else ts * 1000L
        val itemDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)
        return when {
            itemDate == today -> "Today"
            itemDate == yesterday -> "Yesterday"
            itemDate.year == today.year -> DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(zoneId).format(itemDate)
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(zoneId).format(itemDate)
        }
    }

    fun groupHistory(
        items: List<HistoryItem>,
        liveEntriesMap: Map<String, Entry>,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<HistoryGroup> {
        val sorted = items.sortedWith(compareByDescending<HistoryItem> { it.ts }.thenByDescending { it.id })
        val dayGroups = LinkedHashMap<String, MutableList<HistoryUiItem>>()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(zoneId)

        for (item in sorted) {
            val label = formatDayLabel(item.ts, nowMs, zoneId)
            val host = extractHost(item.url)
            val millis = if (item.ts > 100_000_000_000L) item.ts else item.ts * 1000L
            val timeLabel = timeFormatter.format(Instant.ofEpochMilli(millis))
            val liveEntry = item.entryId?.let { liveEntriesMap[it] }

            val uiItem = HistoryUiItem(
                history = item,
                liveEntry = liveEntry,
                host = host,
                timeLabel = timeLabel,
            )
            dayGroups.getOrPut(label) { mutableListOf() }.add(uiItem)
        }

        return dayGroups.map { (label, groupItems) ->
            HistoryGroup(dayLabel = label, items = groupItems)
        }
    }

    fun buildItems(
        bookmarks: List<Bookmark>,
        liveEntriesMap: Map<String, Entry>,
        folderMap: Map<Long, String> = emptyMap(),
        removedDates: Map<String, String> = emptyMap(),
    ): List<SavedEntryItem> {
        return bookmarks.map { bm ->
            SavedEntryItem(
                bookmark = bm,
                liveEntry = liveEntriesMap[bm.entryId],
                folderName = bm.folderId?.let { folderMap[it] },
                removedDateText = removedDates[bm.entryId],
            )
        }
    }

    fun filterAndSort(
        items: List<SavedEntryItem>,
        folderId: Long?,
        query: String,
        sort: SavedSort,
        titleOf: (Bookmark) -> String,
    ): List<SavedEntryItem> {
        val trimmedQuery = query.trim()
        val filtered = items.filter { item ->
            val inFolder = folderId == null || item.bookmark.folderId == folderId
            if (!inFolder) return@filter false
            if (trimmedQuery.isEmpty()) return@filter true
            matchesQuery(item, trimmedQuery, titleOf)
        }
        val comparator: Comparator<SavedEntryItem> = when (sort) {
            SavedSort.RECENT -> compareByDescending<SavedEntryItem> { it.bookmark.savedAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { displayNameOf(it) }
                .thenBy { it.bookmark.entryId }
            SavedSort.A_Z -> compareBy<SavedEntryItem, String>(String.CASE_INSENSITIVE_ORDER) { displayNameOf(it) }
                .thenByDescending { it.bookmark.savedAt }
                .thenBy { it.bookmark.entryId }
            SavedSort.SECTION -> compareBy<SavedEntryItem, String>(String.CASE_INSENSITIVE_ORDER) { titleOf(it.bookmark) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { displayNameOf(it) }
                .thenBy { it.bookmark.entryId }
        }
        return filtered.sortedWith(comparator)
    }

    fun groupSorted(
        items: List<SavedEntryItem>,
        sort: SavedSort,
        titleOf: (Bookmark) -> String,
    ): List<SavedGroup> {
        val groups = items
            .groupBy { titleOf(it.bookmark) }
            .map { (title, groupItems) ->
                val newest = groupItems.maxOfOrNull { it.bookmark.savedAt } ?: 0L
                SavedGroup(
                    sectionTitle = title,
                    newestSavedAt = newest,
                    items = groupItems,
                )
            }
        val groupComparator: Comparator<SavedGroup> = if (sort == SavedSort.RECENT) {
            compareByDescending<SavedGroup> { it.newestSavedAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sectionTitle }
        } else {
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.sectionTitle }
        }
        return groups.sortedWith(groupComparator)
    }

    private fun displayNameOf(item: SavedEntryItem): String =
        item.liveEntry?.name ?: item.bookmark.name

    private fun matchesQuery(
        item: SavedEntryItem,
        trimmedQuery: String,
        titleOf: (Bookmark) -> String,
    ): Boolean {
        val bm = item.bookmark
        val live = item.liveEntry
        return (live?.name?.contains(trimmedQuery, ignoreCase = true) == true) ||
            bm.name.contains(trimmedQuery, ignoreCase = true) ||
            titleOf(bm).contains(trimmedQuery, ignoreCase = true) ||
            bm.sectionTitle.contains(trimmedQuery, ignoreCase = true) ||
            (live?.headingPath?.contains(trimmedQuery, ignoreCase = true) == true) ||
            bm.headingPath.contains(trimmedQuery, ignoreCase = true) ||
            bm.note.contains(trimmedQuery, ignoreCase = true)
    }

    fun findReplacements(
        bookmark: Bookmark,
        sectionEntries: List<Entry>,
        headings: List<Heading> = emptyList(),
    ): List<Entry> {
        val candidates = sectionEntries.filter { it.id != bookmark.entryId }
        if (candidates.isEmpty()) return emptyList()

        // 1. Exact match on headingPath
        if (bookmark.headingPath.isNotBlank()) {
            val exactMatches = candidates.filter {
                it.headingPath.isNotEmpty() && it.headingPath.equals(bookmark.headingPath, ignoreCase = true)
            }
            if (exactMatches.isNotEmpty()) {
                return sortReplacements(exactMatches)
            }
        }

        // 2. Match leaf category from headingPath
        val segments = bookmark.headingPath
            .split('›', '>', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val leaf = segments.lastOrNull()

        if (leaf != null) {
            val leafMatches = candidates.filter { entry ->
                entry.headingPath.contains(leaf, ignoreCase = true)
            }
            if (leafMatches.isNotEmpty()) {
                return sortReplacements(leafMatches)
            }

            // Match against headings in the section
            val matchingHeadingIds = headings
                .filter { it.title.equals(leaf, ignoreCase = true) || it.anchor.equals(leaf, ignoreCase = true) }
                .map { it.id }
                .toSet()
            if (matchingHeadingIds.isNotEmpty()) {
                val headingMatches = candidates.filter { it.headingId in matchingHeadingIds }
                if (headingMatches.isNotEmpty()) {
                    return sortReplacements(headingMatches)
                }
            }
        }

        // 3. Fallback: parent category match if available
        if (segments.size > 1) {
            val parent = segments[segments.size - 2]
            val parentMatches = candidates.filter { it.headingPath.contains(parent, ignoreCase = true) }
            if (parentMatches.isNotEmpty()) {
                return sortReplacements(parentMatches)
            }
        }

        return emptyList()
    }

    private fun sortReplacements(entries: List<Entry>): List<Entry> {
        return entries.sortedWith(
            compareByDescending<Entry> { it.tier == Tier.TOP || it.tier == Tier.STAR }
                .thenByDescending { it.tier.value }
                .thenBy { it.ord }
        ).take(15)
    }
}
