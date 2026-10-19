package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.runtime.Immutable
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.data.model.Tier

@Immutable
data class Category(
    val heading: Heading,
    val items: List<SectionItem>,   // items AFTER the heading up to the next level-1 heading
    val entryCount: Int,            // EntryItem + MoreGroup sizes
    val subHeadings: List<Heading>, // level >= 2 headings inside
)

@Immutable
data class SectionStructure(
    val overview: List<SectionItem>,              // items before the first level-1 heading
    val categories: List<Category>,
    val categoryIndexOfItem: IntArray,            // for every index of the flat list: index of its category, -1 for overview
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SectionStructure
        if (overview != other.overview) return false
        if (categories != other.categories) return false
        if (!categoryIndexOfItem.contentEquals(other.categoryIndexOfItem)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = overview.hashCode()
        result = 31 * result + categories.hashCode()
        result = 31 * result + categoryIndexOfItem.contentHashCode()
        return result
    }
}

fun buildSectionStructure(items: List<SectionItem>): SectionStructure {
    if (items.isEmpty()) {
        return SectionStructure(
            overview = emptyList(),
            categories = emptyList(),
            categoryIndexOfItem = IntArray(0),
        )
    }

    val hasLevel1 = items.any { it is SectionItem.HeadingItem && it.heading.level == 1 }
    val categoryLevel = when {
        hasLevel1 -> 1
        items.any { it is SectionItem.HeadingItem && it.heading.level == 2 } -> 2
        else -> 0
    }

    if (categoryLevel == 0) {
        return SectionStructure(
            overview = items,
            categories = emptyList(),
            categoryIndexOfItem = IntArray(items.size) { -1 },
        )
    }

    val categoryHeadingIndices = mutableListOf<Int>()
    for (i in items.indices) {
        val item = items[i]
        if (item is SectionItem.HeadingItem && item.heading.level == categoryLevel) {
            categoryHeadingIndices.add(i)
        }
    }

    val firstCategoryIdx = categoryHeadingIndices.first()
    val overview = items.subList(0, firstCategoryIdx)
    val categoryIndexOfItem = IntArray(items.size) { -1 }

    val categories = ArrayList<Category>(categoryHeadingIndices.size)
    for (k in categoryHeadingIndices.indices) {
        val headingIdx = categoryHeadingIndices[k]
        val nextHeadingIdx = if (k + 1 < categoryHeadingIndices.size) categoryHeadingIndices[k + 1] else items.size
        val heading = (items[headingIdx] as SectionItem.HeadingItem).heading
        val catItems = items.subList(headingIdx + 1, nextHeadingIdx)

        var entryCount = 0
        for (item in catItems) {
            when (item) {
                is SectionItem.EntryItem -> entryCount += 1 + item.alternatives.size
                is SectionItem.MoreGroup -> entryCount += item.entries.size
                else -> {}
            }
        }

        val subHeadings = catItems.filterIsInstance<SectionItem.HeadingItem>().map { it.heading }

        for (idx in headingIdx until nextHeadingIdx) {
            categoryIndexOfItem[idx] = k
        }

        categories.add(
            Category(
                heading = heading,
                items = catItems,
                entryCount = entryCount,
                subHeadings = subHeadings,
            )
        )
    }

    return SectionStructure(
        overview = overview,
        categories = categories,
        categoryIndexOfItem = categoryIndexOfItem,
    )
}

/**
 * Filter section items based on user preferences.
 * - [starredOnly]: keep only entries with tier STAR or TOP (in EntryItem and inside MoreGroup).
 * - [showIndexes]: if false, drop entries whose entry.kind == EntryKind.INDEX.
 * - Drop headings that have no remaining entries in their subtree.
 * - Drop notes whose heading was dropped.
 */
fun filterSectionItems(
    items: List<SectionItem>,
    starredOnly: Boolean,
    showIndexes: Boolean,
): List<SectionItem> {
    if (!starredOnly && showIndexes) {
        return items
    }

    fun matches(entry: io.github.omeg4dev.fmhy.data.model.Entry): Boolean {
        val matchesStarred = !starredOnly || entry.tier == Tier.STAR || entry.tier == Tier.TOP
        val matchesIndex = showIndexes || entry.kind != EntryKind.INDEX
        return matchesStarred && matchesIndex
    }

    // 1. Filter entries and MoreGroups
    val candidateItems = ArrayList<SectionItem>(items.size)
    for (item in items) {
        when (item) {
            is SectionItem.EntryItem -> {
                val primaryMatches = matches(item.entry)
                val remainingAlternatives = item.alternatives.filter { matches(it) }
                if (primaryMatches) {
                    candidateItems.add(
                        if (remainingAlternatives.size == item.alternatives.size) item
                        else item.copy(alternatives = remainingAlternatives)
                    )
                } else if (remainingAlternatives.isNotEmpty()) {
                    candidateItems.add(
                        SectionItem.EntryItem(
                            entry = remainingAlternatives.first(),
                            alternatives = remainingAlternatives.drop(1)
                        )
                    )
                }
            }
            is SectionItem.MoreGroup -> {
                val filteredEntries = item.entries.filter { matches(it) }
                if (filteredEntries.isNotEmpty()) {
                    candidateItems.add(SectionItem.MoreGroup(filteredEntries))
                }
            }
            is SectionItem.HeadingItem -> {
                candidateItems.add(item)
            }
            is SectionItem.NoteItem -> {
                candidateItems.add(item)
            }
        }
    }

    // 2. Identify which headings have remaining entries in their subtree.
    // A heading is kept if any entry/MoreGroup remains before the next heading of the same or higher level.
    val keptHeadingIds = HashSet<String>()
    for (i in candidateItems.indices) {
        val item = candidateItems[i]
        if (item is SectionItem.HeadingItem) {
            val headingLevel = item.heading.level
            var hasEntry = false
            for (j in (i + 1) until candidateItems.size) {
                val nextItem = candidateItems[j]
                if (nextItem is SectionItem.HeadingItem && nextItem.heading.level <= headingLevel) {
                    break
                }
                if (nextItem is SectionItem.EntryItem || nextItem is SectionItem.MoreGroup) {
                    hasEntry = true
                    break
                }
            }
            if (hasEntry) {
                keptHeadingIds.add(item.heading.id)
            }
        }
    }

    // Map each note to its heading (by note.headingId if set, or nearest preceding heading in original items)
    val noteHeadingMap = HashMap<String, String?>()
    var currentHeadingId: String? = null
    for (item in items) {
        when (item) {
            is SectionItem.HeadingItem -> currentHeadingId = item.heading.id
            is SectionItem.NoteItem -> {
                noteHeadingMap[item.note.id] = item.note.headingId ?: currentHeadingId
            }
            else -> {}
        }
    }

    // 3. Filter candidateItems: keep only kept headings and notes whose heading was not dropped
    val result = ArrayList<SectionItem>(candidateItems.size)
    for (item in candidateItems) {
        when (item) {
            is SectionItem.HeadingItem -> {
                if (item.heading.id in keptHeadingIds) {
                    result.add(item)
                }
            }
            is SectionItem.NoteItem -> {
                val hId = noteHeadingMap[item.note.id]
                if (hId == null || hId in keptHeadingIds) {
                    result.add(item)
                }
            }
            else -> {
                result.add(item)
            }
        }
    }

    return result
}

/**
 * Finds the category heading id that owns the given heading id (or "__overview" if in overview).
 */
fun findCategoryHeadingId(structure: SectionStructure, headingId: String): String? {
    val cat = structure.categories.firstOrNull { c ->
        c.heading.id == headingId || c.subHeadings.any { it.id == headingId } || c.items.any { it is SectionItem.HeadingItem && it.heading.id == headingId }
    }
    return cat?.heading?.id ?: if (structure.overview.any { it is SectionItem.HeadingItem && it.heading.id == headingId }) "__overview" else null
}

