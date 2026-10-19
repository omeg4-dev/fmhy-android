package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.runtime.Immutable
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.SectionItem

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
                is SectionItem.EntryItem -> entryCount++
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
