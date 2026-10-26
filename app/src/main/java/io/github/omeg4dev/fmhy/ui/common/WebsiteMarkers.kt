package io.github.omeg4dev.fmhy.ui.common

internal data class WebsiteMarker(val id: String, val alternateText: String)

internal fun websiteBadgeMarkers(
    isNew: Boolean,
    alternativesCount: Int,
    isBase64: Boolean,
    isSaved: Boolean,
    isCrossLink: Boolean,
): List<WebsiteMarker> {
    val markers = mutableListOf<WebsiteMarker>()
    if (isNew) {
        markers.add(WebsiteMarker("badge:new", "NEW"))
    }
    if (alternativesCount > 0) {
        markers.add(WebsiteMarker("badge:alternatives", "+$alternativesCount"))
    }
    if (isBase64) {
        markers.add(WebsiteMarker("badge:base64", "Hidden link, decoded"))
    }
    if (isSaved) {
        markers.add(WebsiteMarker("badge:saved", "Saved"))
    }
    if (isCrossLink) {
        markers.add(WebsiteMarker("badge:crosslink", "Link to another FMHY section"))
    }
    return markers
}

internal fun inlineAltText(text: String?, fallback: String): String {
    return if (text.isNullOrBlank()) fallback else text
}
