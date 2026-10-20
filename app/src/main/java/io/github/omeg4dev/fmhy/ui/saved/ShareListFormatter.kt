package io.github.omeg4dev.fmhy.ui.saved

import io.github.omeg4dev.fmhy.data.Bookmark
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier

object ShareListFormatter {
    fun format(folderName: String, items: List<SavedEntryItem>): String {
        return formatEntries(folderName, items.map { it.bookmark to it.liveEntry })
    }

    fun formatEntries(folderName: String, items: List<Pair<Bookmark, Entry?>>): String {
        val sb = StringBuilder()
        sb.append("# ").append(folderName).append("\n")
        for ((bookmark, liveEntry) in items) {
            val tier = liveEntry?.tier
            val isStarred = tier == Tier.STAR || tier == Tier.TOP
            val star = if (isStarred) "⭐ " else ""
            val name = liveEntry?.name ?: bookmark.name
            val url = liveEntry?.url ?: bookmark.url
            val desc = liveEntry?.description?.trim()
            sb.append("* ").append(star).append("[").append(name).append("](").append(url).append(")")
            if (!desc.isNullOrEmpty()) {
                sb.append(" - ").append(desc)
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
