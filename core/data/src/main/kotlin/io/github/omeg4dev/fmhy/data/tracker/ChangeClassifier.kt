package io.github.omeg4dev.fmhy.data.tracker

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.parse.FmhyParser

data class LineDiff(
    val kinds: Set<ChangeKind>,          // subset of RENAMED, URL_CHANGED, STARRED, UNSTARRED, EDITED
    val oldName: String?, val newName: String?,
    val oldUrl: String?, val newUrl: String?,
)

internal data class ParsedLink(
    val name: String,
    val url: String,
    val tier: Int,
    val description: String,
)

object ChangeClassifier {
    /**
     * Line without bullet, star markers, bold, and with the FIRST link reduced to nothing:
     * the "description + extra links" part, whitespace-collapsed.
     */
    fun descriptionOf(line: String): String {
        val clean = FmhyParser.stripInvisibleChars(line).trim()
        if (clean.isEmpty()) return ""
        var s = clean
        if (s.startsWith("* ") || s.startsWith("- ")) {
            s = s.substring(2).trimStart()
        }
        s = s.replace("⭐\uFE0F", "").replace("⭐", "").replace("🌟", "")
        s = s.replace("**", "")
        val links = FmhyParser.extractInlineLinks(s)
        val firstLink = links.firstOrNull()
        if (firstLink != null) {
            s = s.substring(0, firstLink.startIndex) + s.substring(firstLink.endIndex)
        }
        return collapseWhitespace(s)
    }

    /**
     * Classifies changes between two entry markdown lines.
     * Name changes (including capitalisation-only changes) are classified as RENAMED.
     */
    /** `detail` of an EDITED row: the old and the new raw line, separated by a newline (lines never contain one). */
    fun editedDetail(oldLine: String, newLine: String): String = "$oldLine\n$newLine"

    fun classify(oldLine: String, newLine: String): LineDiff {
        val oldParsed = parseLinkLine(oldLine)
        val newParsed = parseLinkLine(newLine)

        if (oldLine == newLine) {
            return LineDiff(
                kinds = emptySet(),
                oldName = oldParsed?.name,
                newName = newParsed?.name,
                oldUrl = oldParsed?.url,
                newUrl = newParsed?.url,
            )
        }

        val kinds = mutableSetOf<ChangeKind>()

        // 1. name = label of the first markdown link.
        // RENAMED when trimmed, whitespace-collapsed labels differ (including capitalisation changes).
        if (oldParsed != null && newParsed != null) {
            if (collapseWhitespace(oldParsed.name) != collapseWhitespace(newParsed.name)) {
                kinds.add(ChangeKind.RENAMED)
            }
        }

        // 2. URL_CHANGED when normalizeEntryUrl(oldUrl) != normalizeEntryUrl(newUrl).
        if (oldParsed != null && newParsed != null) {
            if (DatasetStore.normalizeEntryUrl(oldParsed.url) != DatasetStore.normalizeEntryUrl(newParsed.url)) {
                kinds.add(ChangeKind.URL_CHANGED)
            }
        }

        // 3. STARRED / UNSTARRED when the star/tier flag changes.
        val oldTier = oldParsed?.tier ?: FmhyParser.detectTierAndKind(
            FmhyParser.stripInvisibleChars(oldLine).trim().removePrefix("* ").removePrefix("- ").trim()
        ).first
        val newTier = newParsed?.tier ?: FmhyParser.detectTierAndKind(
            FmhyParser.stripInvisibleChars(newLine).trim().removePrefix("* ").removePrefix("- ").trim()
        ).first
        val oldStarred = oldTier >= 1
        val newStarred = newTier >= 1
        if (!oldStarred && newStarred) {
            kinds.add(ChangeKind.STARRED)
        } else if (oldStarred && !newStarred) {
            kinds.add(ChangeKind.UNSTARRED)
        }

        // 4. EDITED when descriptionOf(old) != descriptionOf(new), or when oldLine != newLine and none of the other kinds fired.
        val descOld = descriptionOf(oldLine)
        val descNew = descriptionOf(newLine)
        if (descOld != descNew || kinds.isEmpty()) {
            kinds.add(ChangeKind.EDITED)
        }

        return LineDiff(
            kinds = kinds,
            oldName = oldParsed?.name,
            newName = newParsed?.name,
            oldUrl = oldParsed?.url,
            newUrl = newParsed?.url,
        )
    }

    internal fun collapseWhitespace(s: String): String =
        s.trim().replace(Regex("""\s+"""), " ")

    fun buildComparableLine(
        name: String,
        url: String,
        description: String,
        tier: Int,
        extraLinks: List<Pair<String, String>> = emptyList(),
    ): String {
        val star = when {
            tier >= 2 -> "🌟 "
            tier >= 1 -> "⭐ "
            else -> ""
        }
        val bold = if (tier >= 1) "**" else ""
        val extras = if (extraLinks.isNotEmpty()) {
            extraLinks.joinToString(" / ") { "[${it.first}](${it.second})" }
        } else ""
        val descParts = listOf(description, extras).filter { it.isNotBlank() }
        val desc = if (descParts.isNotEmpty()) " - ${descParts.joinToString(" / ")}" else ""
        return "* $star$bold[$name]($url)$bold$desc"
    }

    internal fun parseLinkLine(line: String): ParsedLink? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val stripped = trimmed.removePrefix("* ").removePrefix("- ").trim()
        val (tier, _, rest) = FmhyParser.detectTierAndKind(stripped)
        val (head, desc) = FmhyParser.splitHeadDesc(rest)
        val headLinks = FmhyParser.extractInlineLinks(head)
        val descLinks = if (headLinks.isEmpty()) FmhyParser.extractInlineLinks(desc) else emptyList()
        val firstLink = headLinks.firstOrNull() ?: descLinks.firstOrNull() ?: return null

        val (_, nonLinkDesc) = FmhyParser.splitHeadDesc(desc)
        val description = if (nonLinkDesc.isNotEmpty()) {
            FmhyParser.markdownToPlain(nonLinkDesc).trim()
        } else if (desc.isNotEmpty() && headLinks.isNotEmpty()) {
            FmhyParser.markdownToPlain(desc).trim()
        } else ""

        return ParsedLink(
            name = firstLink.label,
            url = firstLink.url,
            tier = tier,
            description = description,
        )
    }
}
