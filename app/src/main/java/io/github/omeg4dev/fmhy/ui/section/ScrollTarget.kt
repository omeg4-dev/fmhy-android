package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import io.github.omeg4dev.fmhy.data.model.SectionItem
import kotlinx.coroutines.flow.first
import kotlin.math.abs

const val ENTRY_VIEWPORT_FRACTION = 0.2f
const val HEADING_VIEWPORT_FRACTION = 0.05f

sealed interface ScrollTarget {
    data class Found(val index: Int, val isHeading: Boolean, val id: String) : ScrollTarget
    data class MissingEntry(val fallbackHeadingIndex: Int?, val fallbackHeadingId: String?) : ScrollTarget
    data object None : ScrollTarget
}

fun resolveScrollTarget(
    items: List<SectionItem>,
    focusEntryId: String?,
    focusHeadingId: String?,
): ScrollTarget {
    val entryId = focusEntryId?.takeIf { it.isNotEmpty() }
    val headingId = focusHeadingId?.takeIf { it.isNotEmpty() }

    if (entryId != null) {
        val entryIdx = items.indexOfFirst { item ->
            when (item) {
                is SectionItem.EntryItem -> item.entry.id == entryId || item.alternatives.any { it.id == entryId }
                is SectionItem.MoreGroup -> item.entries.any { it.id == entryId }
                else -> false
            }
        }
        if (entryIdx != -1) {
            return ScrollTarget.Found(index = entryIdx, isHeading = false, id = entryId)
        }
        if (headingId != null) {
            val headingMatch = findHeadingMatch(items, headingId)
            if (headingMatch != null) {
                return ScrollTarget.MissingEntry(
                    fallbackHeadingIndex = headingMatch.first,
                    fallbackHeadingId = headingMatch.second,
                )
            }
        }
        return ScrollTarget.MissingEntry(fallbackHeadingIndex = null, fallbackHeadingId = null)
    }

    if (headingId != null) {
        val headingMatch = findHeadingMatch(items, headingId)
        if (headingMatch != null) {
            return ScrollTarget.Found(index = headingMatch.first, isHeading = true, id = headingMatch.second)
        }
        return ScrollTarget.None
    }

    return ScrollTarget.None
}

private fun findHeadingMatch(items: List<SectionItem>, headingTarget: String): Pair<Int, String>? {
    if (headingTarget.isEmpty()) return null
    val idx = items.indexOfFirst { item ->
        item is SectionItem.HeadingItem && (
            item.heading.id == headingTarget ||
            item.heading.anchor.equals(headingTarget, ignoreCase = true) ||
            item.heading.id.endsWith(headingTarget)
        )
    }
    return if (idx != -1) {
        val heading = (items[idx] as SectionItem.HeadingItem).heading
        idx to heading.id
    } else {
        null
    }
}

suspend fun LazyListState.jumpTo(index: Int, viewportFraction: Float = ENTRY_VIEWPORT_FRACTION) {
    if (index < 0) return
    snapshotFlow {
        layoutInfo.viewportSize.height > 0 && layoutInfo.totalItemsCount > index
    }.first { it }

    val initialViewportHeight = layoutInfo.viewportSize.height
    val initialIntendedOffset = if (index == 0) 0 else (initialViewportHeight * viewportFraction).toInt()
    val initialScrollOffset = if (index == 0) 0 else -initialIntendedOffset
    scrollToItem(index, scrollOffset = initialScrollOffset)

    var consecutiveStableFrames = 0
    var lastOffset: Int? = null

    for (iteration in 0 until 12) {
        withFrameNanos { }
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val viewportHeight = layoutInfo.viewportSize.height
        val intendedOffset = if (index == 0) 0 else (viewportHeight * viewportFraction).toInt()
        val scrollOffset = if (index == 0) 0 else -intendedOffset

        if (itemInfo == null) {
            consecutiveStableFrames = 0
            lastOffset = null
            scrollToItem(index, scrollOffset = scrollOffset)
        } else {
            val currentOffset = itemInfo.offset
            val deviation = abs(currentOffset - intendedOffset)
            val isStable = deviation <= 8 || (lastOffset == currentOffset && !canScrollForward)
            if (isStable) {
                consecutiveStableFrames++
                if (consecutiveStableFrames >= 2) {
                    break
                }
            } else {
                consecutiveStableFrames = 0
                scrollToItem(index, scrollOffset = scrollOffset)
            }
            lastOffset = currentOffset
        }
    }
}
