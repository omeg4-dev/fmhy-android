package io.github.omeg4dev.fmhy.data.link

import io.github.omeg4dev.fmhy.data.model.Heading

/** Where an FMHY-internal link points inside the app. [headingId] is null for a whole section. */
data class LinkTarget(val sectionId: String, val headingId: String?)

/**
 * FMHY cross-references: headings written as `[Label](url)` and entries that link to another
 * FMHY page (old reddit wiki or fmhy.net) instead of a site.
 */
class CrossLinks(headings: List<Heading>) {

    private val sectionIds: Set<String> = headings.mapTo(HashSet()) { it.sectionId }
    private val byNormalizedTitle: Map<String, Map<String, Heading>> = headings
        .groupBy { it.sectionId }
        .mapValues { (_, list) ->
            // First heading wins when two share a title, like the web page's anchors do.
            list.reversed().associateBy { normalize(splitLinkTitle(it.title).first) }
        }
    private val byAnchor: Map<String, Map<String, Heading>> = headings
        .groupBy { it.sectionId }
        .mapValues { (_, list) -> list.reversed().associateBy { it.anchor.lowercase() } }

    /** The in-app target of [url], or null when it is not an FMHY page the dataset contains. */
    fun resolve(url: String): LinkTarget? {
        REDDIT_WIKI.matchEntire(url.trim())?.let { m ->
            val sectionId = REDDIT_PAGES[m.groupValues[1].lowercase()] ?: return null
            if (sectionId !in sectionIds) return null
            val titles = byNormalizedTitle[sectionId].orEmpty()
            val heading = m.groups[2]?.value?.let { anchor ->
                val key = normalize(decodeRedditAnchor(anchor))
                // Reddit numbers repeated anchors ("software_sites2").
                titles[key] ?: titles[key.trimEnd { it.isDigit() }]
            }
            return LinkTarget(sectionId, heading?.id)
        }
        FMHY_SITE.matchEntire(url.trim())?.let { m ->
            val sectionId = m.groupValues[1].lowercase()
            if (sectionId !in sectionIds) return null
            val heading = m.groups[2]?.value?.let { byAnchor[sectionId]?.get(it.lowercase()) }
            return LinkTarget(sectionId, heading?.id)
        }
        return null
    }

    companion object {
        private val MD_LINK = Regex("""^\s*\[(.+)]\((\S+)\)\s*$""")
        private val REDDIT_WIKI = Regex(
            """^https?://(?:www\.|old\.)?reddit\.com/r/FREEMEDIAHECKYEAH/wiki/([A-Za-z-]+)/?(?:#wiki_(.+))?$""",
            RegexOption.IGNORE_CASE,
        )
        private val FMHY_SITE = Regex("""^https?://(?:www\.)?fmhy\.net/([a-z-]+)/?(?:#(.+))?$""", RegexOption.IGNORE_CASE)

        /** Reddit wiki page name → section id (file name in fmhy/edit). */
        private val REDDIT_PAGES = mapOf(
            "adblock-vpn-privacy" to "privacy",
            "ai" to "ai",
            "android" to "mobile",
            "audio" to "audio",
            "dev-tools" to "developer-tools",
            "download" to "downloading",
            "edu" to "educational",
            "file-tools" to "file-tools",
            "game-tools" to "gaming-tools",
            "games" to "gaming",
            "image-tools" to "image-tools",
            "internet-tools" to "internet-tools",
            "linux" to "linux-macos",
            "misc" to "misc",
            "non-eng" to "non-english",
            "reading" to "reading",
            "social-media" to "social-media-tools",
            "storage" to "storage",
            "system-tools" to "system-tools",
            "text-tools" to "text-tools",
            "torrent" to "torrenting",
            "video" to "video",
            "video-tools" to "video-tools",
        )

        /** `[Label](url)` → (Label, url); anything else → (title, null). */
        fun splitLinkTitle(title: String): Pair<String, String?> {
            val m = MD_LINK.matchEntire(title) ?: return title to null
            return m.groupValues[1].trim() to m.groupValues[2]
        }

        /** `.25BA_spotify_tools` → `spotify tools`: drops the ►/▷ markers, decodes `.XX` escapes. */
        internal fun decodeRedditAnchor(anchor: String): String = anchor
            .replace(Regex("""\.25B[A7]"""), " ")
            .replace(Regex("""\.([0-9A-Fa-f]{2})""")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace('_', ' ')
            .replace("&amp;", "&")

        /** Lowercase letters and digits only, so titles and anchors compare across formats. */
        internal fun normalize(text: String): String =
            text.lowercase().filter { it.isLetterOrDigit() }
    }
}
