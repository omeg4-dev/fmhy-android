package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.ui.common.DateFormatters
import java.net.URI
import java.time.Instant
import java.time.ZoneId

data class ChangeCard(
    val key: String,
    val ts: Long,
    val primaryKind: ChangeKind,
    val kinds: Set<ChangeKind>,
    val name: String,
    val url: String,
    val sectionId: String,
    val entry: Entry?,
    val oldName: String?,
    val oldUrl: String?,
    val oldSectionId: String?,
    val oldLine: String?,
)

data class DayGroup(
    val dayHeader: String,
    val cards: List<ChangeCard>,
)

enum class WhatsNewPeriod(val days: Int, val label: String) {
    WEEK(7, "Last 7 days"),
    MONTH(30, "Last 30 days"),
    QUARTER(90, "Last 90 days"),
}

sealed interface WhatsNewScope {
    data object All : WhatsNewScope
    data object Following : WhatsNewScope
    data object Saved : WhatsNewScope
    data class Sections(val sections: Set<String>) : WhatsNewScope
}

object WhatsNewGrouping {

    /** Small text edits outnumber everything else, so the feed starts without them (one tap in the type menu). */
    val DEFAULT_TYPES: Set<ChangeKind> = ChangeKind.entries.toSet() - ChangeKind.EDITED

    /** Priority order: REMOVED > ADDED > MOVED > RENAMED > URL_CHANGED > STARRED > UNSTARRED > EDITED */
    val PRIMARY_KIND_PRIORITY: List<ChangeKind> = listOf(
        ChangeKind.REMOVED,
        ChangeKind.ADDED,
        ChangeKind.MOVED,
        ChangeKind.RENAMED,
        ChangeKind.URL_CHANGED,
        ChangeKind.STARRED,
        ChangeKind.UNSTARRED,
        ChangeKind.EDITED,
    )

    fun cardKey(item: ChangeItem, zoneId: ZoneId = ZoneId.systemDefault()): String {
        // The address identifies a site across producers and sections (entry ids differ per section),
        // so a remove in one section and an add in another land in the same card.
        val baseKey = DatasetStore.normalizeEntryUrl(item.url).ifEmpty { item.entryId.ifBlank { item.name } }
        val localDay = Instant.ofEpochSecond(item.ts).atZone(zoneId).toLocalDate().toEpochDay()
        return "$baseKey:$localDay"
    }

    /**
     * Collapses ChangeItems into ChangeCards:
     * - Grouped by key (entryId or normalised url) + local day
     * - Latest timestamp and latest properties
     * - Primary kind priority: REMOVED > ADDED > MOVED > RENAMED > URL_CHANGED > STARRED > UNSTARRED > EDITED
     * - If ADDED + REMOVED on same day with same URL: treat as EDITED (re-added), keep both kinds in kinds
     */
    fun collapseChanges(
        changes: List<ChangeItem>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<ChangeCard> {
        if (changes.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<ChangeItem>>()
        for (item in changes) {
            val key = cardKey(item, zoneId)
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }

        val cards = mutableListOf<ChangeCard>()
        for ((key, items) in grouped) {
            val sortedItems = items.sortedBy { it.ts }
            val latest = sortedItems.last()
            val rawKinds = items.map { it.kind }.toSet()
            val entry = items.mapNotNull { it.entry }.lastOrNull()
            val name = entry?.name ?: latest.name
            val url = latest.url
            val sectionId = latest.sectionId

            val oldName = items.firstOrNull { it.kind == ChangeKind.RENAMED }?.detail?.takeIf { it.isNotBlank() }
            val oldUrl = items.firstOrNull { it.kind == ChangeKind.URL_CHANGED }?.detail?.takeIf { it.isNotBlank() }
            var oldSectionId = items.firstOrNull { it.kind == ChangeKind.MOVED }?.detail?.takeIf { it.isNotBlank() }
            val oldLine = items.firstOrNull { it.kind == ChangeKind.EDITED }?.detail?.takeIf { it.isNotBlank() }

            val hasAdded = ChangeKind.ADDED in rawKinds
            val hasRemoved = ChangeKind.REMOVED in rawKinds
            val addedItem = items.firstOrNull { it.kind == ChangeKind.ADDED }
            val removedItem = items.firstOrNull { it.kind == ChangeKind.REMOVED }

            val isReAdded = hasAdded && hasRemoved && addedItem != null && removedItem != null &&
                (addedItem.url == removedItem.url ||
                    (addedItem.url.isNotEmpty() && DatasetStore.normalizeEntryUrl(addedItem.url) == DatasetStore.normalizeEntryUrl(removedItem.url)))

            val (primaryKind, kinds) = if (isReAdded) {
                val rest = rawKinds - ChangeKind.ADDED - ChangeKind.REMOVED
                if (addedItem!!.sectionId != removedItem!!.sectionId) {
                    // Removed from one section and added to another: a move.
                    oldSectionId = oldSectionId ?: removedItem.sectionId
                    ChangeKind.MOVED to (rest + ChangeKind.MOVED)
                } else {
                    ChangeKind.EDITED to (rest + ChangeKind.EDITED)
                }
            } else {
                val primary = PRIMARY_KIND_PRIORITY.firstOrNull { it in rawKinds } ?: latest.kind
                primary to rawKinds
            }

            cards.add(
                ChangeCard(
                    key = key,
                    ts = latest.ts,
                    primaryKind = primaryKind,
                    kinds = kinds,
                    name = name,
                    url = url,
                    sectionId = sectionId,
                    entry = entry,
                    oldName = oldName,
                    oldUrl = oldUrl,
                    oldSectionId = oldSectionId,
                    oldLine = oldLine,
                )
            )
        }

        return cards.sortedByDescending { it.ts }
    }

    /**
     * Filters cards by scope: All, Following, Saved, Sections(set)
     */
    fun filterByScope(
        cards: List<ChangeCard>,
        scope: WhatsNewScope,
        follows: List<Follow> = emptyList(),
        savedIds: Set<String> = emptySet(),
        savedUrls: Set<String> = emptySet(),
        hierarchy: Map<String, Set<String>> = emptyMap(),
    ): List<ChangeCard> {
        return when (scope) {
            is WhatsNewScope.All -> cards
            is WhatsNewScope.Following -> {
                if (follows.isEmpty()) return emptyList()
                val sectionFollows = follows.filter { it.headingId.isEmpty() }.map { it.sectionId }.toSet()
                val categoryFollowHeadingIds = follows.filter { it.headingId.isNotEmpty() }.flatMap { f ->
                    hierarchy[f.headingId] ?: setOf(f.headingId)
                }.toSet()

                cards.filter { card ->
                    if (card.sectionId in sectionFollows) return@filter true
                    val hId = card.entry?.headingId
                    if (hId != null && hId in categoryFollowHeadingIds) return@filter true
                    false
                }
            }
            is WhatsNewScope.Saved -> {
                cards.filter { card ->
                    card.entry?.id in savedIds ||
                        card.key.substringBefore(':') in savedIds ||
                        DatasetStore.normalizeEntryUrl(card.url) in savedUrls
                }
            }
            is WhatsNewScope.Sections -> {
                if (scope.sections.isEmpty()) cards else cards.filter { it.sectionId in scope.sections }
            }
        }
    }

    /**
     * Filters cards by type (empty = all)
     */
    fun filterByTypes(
        cards: List<ChangeCard>,
        types: Set<ChangeKind>,
    ): List<ChangeCard> {
        if (types.isEmpty()) return cards
        return cards.filter { card -> card.kinds.any { it in types } }
    }

    /**
     * Free-text filter for the What's new search: every whitespace-separated word must occur
     * (case-insensitive) in the name, old name, host of the url / old url, or the section title.
     */
    fun filterByText(
        cards: List<ChangeCard>,
        query: String,
        sectionTitles: Map<String, String> = emptyMap(),
    ): List<ChangeCard> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return cards
        return cards.filter { card ->
            val hay = buildString {
                append(card.name).append(' ')
                card.oldName?.let { append(it).append(' ') }
                append(extractHost(card.url)).append(' ')
                card.oldUrl?.let { append(extractHost(it)).append(' ') }
                append(sectionTitles[card.sectionId] ?: card.sectionId)
            }.lowercase()
            words.all { it in hay }
        }
    }

    /**
     * Counts per kind computed after scope filter, before type filter.
     */
    fun countsPerKind(cards: List<ChangeCard>): Map<ChangeKind, Int> {
        return ChangeKind.entries.associateWith { kind ->
            cards.count { kind in it.kinds }
        }
    }

    /**
     * Group cards by day (sticky headers "Today · 12 changes", "Yesterday · 1 change", "Mon 18 Oct · 5 changes")
     */
    fun groupByDay(
        cards: List<ChangeCard>,
        nowSec: Long = System.currentTimeMillis() / 1000,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DayGroup> {
        if (cards.isEmpty()) return emptyList()

        val groups = LinkedHashMap<String, MutableList<ChangeCard>>()
        for (card in cards) {
            val dateLabel = DateFormatters.formatDayHeader(card.ts, nowSec, zoneId)
            groups.getOrPut(dateLabel) { mutableListOf() }.add(card)
        }

        return groups.map { (dateLabel, dayCards) ->
            val count = dayCards.size
            val header = if (count == 1) "$dateLabel · 1 change" else "$dateLabel · $count changes"
            DayGroup(
                dayHeader = header,
                cards = dayCards.sortedByDescending { it.ts },
            )
        }
    }

    fun extractHost(url: String): String {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val uri = URI(cleanUrl)
            uri.host?.removePrefix("www.").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun extractPath(url: String): String {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val uri = URI(cleanUrl)
            val p = uri.path.orEmpty()
            val q = if (uri.query != null) "?${uri.query}" else ""
            (p + q).ifEmpty { "/" }
        } catch (_: Exception) {
            url
        }
    }

    fun formatUrlChange(oldUrl: String?, newUrl: String): String {
        if (oldUrl.isNullOrBlank()) {
            return extractHost(newUrl).ifEmpty { newUrl }
        }
        val oldHost = extractHost(oldUrl)
        val newHost = extractHost(newUrl)
        return if (oldHost.isNotEmpty() && newHost.isNotEmpty() && oldHost != newHost) {
            "$oldHost → $newHost"
        } else {
            val oldPath = extractPath(oldUrl)
            val newPath = extractPath(newUrl)
            "$oldPath → $newPath"
        }
    }
}
