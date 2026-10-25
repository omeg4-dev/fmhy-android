package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.model.Link

/** One piece of an entry line as fmhy.net renders it: `Name, 2 - Description / Link [icons]`. */
sealed interface WebSegment {
    data class Name(val text: String) : WebSegment
    data class Mirror(val label: String, val url: String) : WebSegment
    data class Separator(val text: String) : WebSegment          // ", ", " - ", " / ", " "
    data class Description(val text: String) : WebSegment
    data class Link(val label: String, val url: String) : WebSegment
    data class Social(val index: Int, val label: String, val url: String) : WebSegment  // index = position in extraLinks
}

fun websiteSegments(
    name: String,
    description: String,
    mirrors: List<Link>,
    extraLinks: List<Link>,
    isSocial: (String) -> Boolean = { socialIconFor(it) != null },
): List<WebSegment> = buildList {
    add(WebSegment.Name(name))

    for (mirror in mirrors) {
        add(WebSegment.Separator(", "))
        add(WebSegment.Mirror(mirror.label, mirror.url))
    }

    val indexedExtra = extraLinks.withIndex().toList()
    val socialLinks = indexedExtra.filter { isSocial(it.value.label) }
    val otherLinks = indexedExtra.filter { !isSocial(it.value.label) }

    val hasDesc = description.isNotBlank()
    val hasOther = otherLinks.isNotEmpty()

    if (hasDesc || hasOther) {
        add(WebSegment.Separator(" - "))
    }

    if (hasDesc) {
        add(WebSegment.Description(description))
    }

    otherLinks.forEachIndexed { i, indexedLink ->
        if (hasDesc || i > 0) {
            add(WebSegment.Separator(" / "))
        }
        add(WebSegment.Link(indexedLink.value.label, indexedLink.value.url))
    }

    for (indexedSocial in socialLinks) {
        add(WebSegment.Separator(" "))
        add(WebSegment.Social(indexedSocial.index, indexedSocial.value.label, indexedSocial.value.url))
    }
}
