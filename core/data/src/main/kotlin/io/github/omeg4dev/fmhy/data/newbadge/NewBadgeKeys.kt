package io.github.omeg4dev.fmhy.data.newbadge

import io.github.omeg4dev.fmhy.data.parse.Ids

object NewBadgeKeys {
    fun urlKey(url: String): String =
        Ids.normalizeUrl(url).substringAfter("://")

    fun pageKey(sectionId: String, url: String): String =
        "$sectionId|${urlKey(url)}"
}
