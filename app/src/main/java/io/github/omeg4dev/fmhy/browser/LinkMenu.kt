package io.github.omeg4dev.fmhy.browser

enum class HitKind {
    LINK,
    IMAGE_LINK,
    IMAGE,
    OTHER,
}

enum class LinkMenuAction {
    OPEN_NEW_TAB,
    OPEN_BACKGROUND,
    OPEN_PRIVATE,
    COPY_LINK,
    SHARE_LINK,
    OPEN_IMAGE_NEW_TAB,
    COPY_IMAGE_LINK,
}

data class LinkMenuItem(
    val action: LinkMenuAction,
    val label: String,
)

data class LinkMenuRequest(
    val tabId: Long,
    val kind: HitKind,
    val url: String,
    val imageUrl: String? = null,
)

fun hasRealAddress(address: String?): Boolean {
    if (address.isNullOrBlank()) return false
    val trimmed = address.trim()
    if (trimmed.startsWith("javascript:", ignoreCase = true)) return false
    if (trimmed == "#" || trimmed.startsWith("about:blank", ignoreCase = true)) return false
    return true
}

fun linkMenuItems(
    kind: HitKind,
    address: String?,
    hasRealAddress: Boolean = hasRealAddress(address),
): List<LinkMenuItem> {
    if (address.isNullOrBlank()) return emptyList()
    val isJs = address.trim().startsWith("javascript:", ignoreCase = true)

    if (isJs) {
        return if (hasRealAddress) {
            when (kind) {
                HitKind.LINK, HitKind.IMAGE_LINK -> listOf(
                    LinkMenuItem(LinkMenuAction.COPY_LINK, "Copy link")
                )
                HitKind.IMAGE -> listOf(
                    LinkMenuItem(LinkMenuAction.COPY_IMAGE_LINK, "Copy image link")
                )
                HitKind.OTHER -> emptyList()
            }
        } else {
            emptyList()
        }
    }

    if (!hasRealAddress) return emptyList()

    return when (kind) {
        HitKind.LINK, HitKind.IMAGE_LINK -> listOf(
            LinkMenuItem(LinkMenuAction.OPEN_NEW_TAB, "Open in new tab"),
            LinkMenuItem(LinkMenuAction.OPEN_BACKGROUND, "Open in background tab"),
            LinkMenuItem(LinkMenuAction.OPEN_PRIVATE, "Open in private tab"),
            LinkMenuItem(LinkMenuAction.COPY_LINK, "Copy link"),
            LinkMenuItem(LinkMenuAction.SHARE_LINK, "Share link"),
        )
        HitKind.IMAGE -> listOf(
            LinkMenuItem(LinkMenuAction.OPEN_IMAGE_NEW_TAB, "Open image in new tab"),
            LinkMenuItem(LinkMenuAction.COPY_IMAGE_LINK, "Copy image link"),
        )
        HitKind.OTHER -> emptyList()
    }
}

fun shortenAddress(url: String, max: Int = 48): String {
    if (max <= 0) return ""
    if (url.length <= max) return url
    if (max == 1) return "…"
    return url.take(max - 1) + "…"
}

fun tabPrivacyFor(openerPrivate: Boolean, action: LinkMenuAction): Boolean = when (action) {
    LinkMenuAction.OPEN_PRIVATE -> true
    LinkMenuAction.OPEN_NEW_TAB,
    LinkMenuAction.OPEN_BACKGROUND,
    LinkMenuAction.OPEN_IMAGE_NEW_TAB -> openerPrivate
    else -> openerPrivate
}
