package io.github.omeg4dev.fmhy.data.nsfw

import io.github.omeg4dev.fmhy.data.extractHost
import io.github.omeg4dev.fmhy.data.parse.Base64Map
import io.github.omeg4dev.fmhy.data.parse.FmhyParser
import io.github.omeg4dev.fmhy.data.parse.SectionInfo
import io.github.omeg4dev.fmhy.data.parse.Slug

/**
 * Mandatory content filter applied on every read of the NSFW markdown list.
 * Drops prohibited categories, lines, primary hosts originating in dropped categories,
 * the table of contents, prohibited notes, and any headings left without entries.
 */
object NsfwFilter {

    private val FRONT_MATTER_RE = Regex("""^---\s*$""")
    private val HEADING_RE = Regex("""^(#{1,4})\s+(.*)""")
    private val BULLET_RE = Regex("""^(\s*)([*-])\s+(.*)""")
    private val NOTE_MARKER_RE = Regex("""^\*\*Note\*\*\s*-\s*(.*)""")
    private val BLOCKQUOTE_RE = Regex("""^>\s*(.*)""")
    private val BACK_TO_TOP_RE = Regex("""^\[.*]\(#\S*\)\s*$""")

    private val BANNED_TERMS = listOf(
        "leak",
        "coomer",
        "kemono",
        "onlyhaven",
        "pawchive",
        "bakemono",
        "onlyfans",
        "fansly",
        "thot",
        "camrip",
        "cam rip",
        "siterip",
        "site rip",
        "cyberdrop",
        "fanripper",
        "of-scraper",
        // Face search over adult material: it can be used to identify private people.
        "pornfaceid",
        "pornstarbyface",
    )

    private val IGNORED_GENERAL_HOSTS = setOf(
        "github.com",
        "gitlab.com",
        "reddit.com",
        "t.me",
        "telegram.me",
        "discord.gg",
        "discord.com",
        "greasyfork.org",
        "fmhy.net",
        "rentry.co",
        "rentry.org",
        "pastebin.com",
    )

    private val PARSE_SECTION_INFO = SectionInfo(
        id = NSFW_SECTION_ID,
        title = NSFW_SECTION_TITLE,
        description = "",
        file = "nsfw.md",
        ord = Int.MAX_VALUE,
    )

    /** Returns filtered wiki Markdown safe to parse and display. */
    fun filter(markdown: String): String {
        // Same cleanup as the parser, so a hidden character cannot make a heading look different here.
        val rawLines = markdown.lines().map { FmhyParser.stripInvisibleChars(it) }
        val pass1Result = dropCategoriesAndToc(rawLines)
        val pass2Lines = dropBannedLinesAndHosts(pass1Result.lines, pass1Result.droppedHosts)
        val finalLines = dropEmptyHeadings(pass2Lines)
        return finalLines.joinToString("\n")
    }

    private data class Pass1Result(
        val lines: List<String?>,
        val droppedHosts: Set<String>,
    )

    private fun dropCategoriesAndToc(lines: List<String>): Pass1Result {
        val out = MutableList<String?>(lines.size) { null }
        val droppedHosts = HashSet<String>()

        var i = 0
        if (lines.isNotEmpty() && FRONT_MATTER_RE.matches(lines[0])) {
            out[0] = lines[0]
            i = 1
            while (i < lines.size) {
                out[i] = lines[i]
                if (FRONT_MATTER_RE.matches(lines[i])) {
                    i++
                    break
                }
                i++
            }
        }

        var inFence = false
        var inToc = false
        var droppedCategoryLevel: Int? = null

        while (i < lines.size) {
            val line = lines[i]
            val idx = i++

            if (line.startsWith(":::")) {
                inFence = !inFence
                if (!inToc && droppedCategoryLevel == null) {
                    out[idx] = line
                }
                continue
            }
            if (inFence) {
                if (!inToc && droppedCategoryLevel == null) {
                    out[idx] = line
                }
                continue
            }

            if (!isStructuralSkippedLine(line)) {
                val headingMatch = HEADING_RE.matchEntire(line)
                if (headingMatch != null) {
                    val level = headingMatch.groupValues[1].length
                    val plain = plainHeadingTitle(headingMatch.groupValues[2].trim()).lowercase()

                    inToc = false
                    if (droppedCategoryLevel != null && level <= droppedCategoryLevel) {
                        droppedCategoryLevel = null
                    }

                    if (plain == "table of contents") {
                        inToc = true
                        continue
                    }

                    if (droppedCategoryLevel != null || isDroppedCategoryTitle(plain)) {
                        if (droppedCategoryLevel == null) {
                            droppedCategoryLevel = level
                        }
                        continue
                    }

                    out[idx] = line
                    continue
                }
            }

            if (inToc) continue

            if (droppedCategoryLevel != null) {
                for (link in FmhyParser.extractInlineLinks(line)) {
                    val host = extractHttpHost(link.url) ?: continue
                    if (host !in IGNORED_GENERAL_HOSTS) {
                        droppedHosts.add(host)
                    }
                }
                continue
            }

            out[idx] = line
        }

        return Pass1Result(out, droppedHosts)
    }

    private fun dropBannedLinesAndHosts(
        lines: List<String?>,
        droppedHosts: Set<String>,
    ): List<String?> {
        val out = lines.toMutableList()
        var i = 0
        val first = out.firstOrNull()
        if (first != null && FRONT_MATTER_RE.matches(first)) {
            i = 1
            while (i < out.size) {
                val cur = out[i]
                if (cur != null && FRONT_MATTER_RE.matches(cur)) {
                    i++
                    break
                }
                i++
            }
        }

        var inFence = false
        while (i < out.size) {
            val idx = i++
            val line = out[idx] ?: continue

            if (line.startsWith(":::")) {
                inFence = !inFence
                continue
            }
            if (inFence) continue
            if (isStructuralSkippedLine(line)) continue
            if (HEADING_RE.matches(line)) continue

            if (BLOCKQUOTE_RE.matches(line)) {
                if (containsBannedTerm(line)) {
                    out[idx] = null
                }
                continue
            }

            val bulletMatch = BULLET_RE.matchEntire(line)
            if (bulletMatch != null) {
                val content = bulletMatch.groupValues[3]
                if (NOTE_MARKER_RE.matches(content)) {
                    if (containsBannedTerm(line)) {
                        out[idx] = null
                    }
                } else if (shouldDropEntryLine(line, droppedHosts)) {
                    out[idx] = null
                }
                continue
            }

            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && FmhyParser.extractInlineLinks(trimmed).isNotEmpty()) {
                if (shouldDropEntryLine(line, droppedHosts)) {
                    out[idx] = null
                }
            }
        }
        return out
    }

    private fun dropEmptyHeadings(lines: List<String?>): List<String> {
        val pass2Text = lines.joinToString("\n") { it ?: "" }
        val parsed = FmhyParser.parseSection(PARSE_SECTION_INFO, pass2Text, Base64Map(""))
        val parentById = parsed.headings.associate { it.id to it.parentId }
        val nonEmptyHeadingIds = HashSet<String>()
        for (entry in parsed.entries) {
            var cur = entry.headingId
            while (cur != null && nonEmptyHeadingIds.add(cur)) {
                cur = parentById[cur]
            }
        }

        val kept = ArrayList<String>(lines.size)
        var i = 0
        val first = lines.firstOrNull()
        if (first != null && FRONT_MATTER_RE.matches(first)) {
            kept.add(first)
            i = 1
            while (i < lines.size) {
                val cur = lines[i]
                if (cur != null) kept.add(cur)
                if (cur != null && FRONT_MATTER_RE.matches(cur)) {
                    i++
                    break
                }
                i++
            }
        }

        var inFence = false
        var headingIdx = 0
        var activeHeadingKept = true

        while (i < lines.size) {
            val line = lines[i++] ?: continue

            if (line.startsWith(":::")) {
                inFence = !inFence
                if (activeHeadingKept) kept.add(line)
                continue
            }
            if (inFence) {
                if (activeHeadingKept) kept.add(line)
                continue
            }
            if (isStructuralSkippedLine(line)) {
                if (activeHeadingKept && line.isNotBlank()) kept.add(line)
                continue
            }

            if (HEADING_RE.matches(line)) {
                val heading = parsed.headings.getOrNull(headingIdx++)
                activeHeadingKept = heading != null && heading.id in nonEmptyHeadingIds
                if (activeHeadingKept) {
                    if (kept.isNotEmpty() && kept.last().isNotEmpty()) {
                        kept.add("")
                    }
                    kept.add(line)
                }
                continue
            }

            if (activeHeadingKept) {
                kept.add(line)
            }
        }

        while (kept.lastOrNull()?.isBlank() == true) {
            kept.removeAt(kept.size - 1)
        }
        return kept
    }

    private fun isStructuralSkippedLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.isEmpty() ||
            line.trimStart().startsWith("<") ||
            trimmed == "***" ||
            trimmed == "---" ||
            line.contains("back to wiki index", ignoreCase = true) ||
            BACK_TO_TOP_RE.matches(trimmed)
    }

    private fun plainHeadingTitle(rawTitle: String): String {
        val plain = FmhyParser.markdownToPlain(rawTitle)
        return Slug.stripTitlePrefix(plain).trim()
    }

    private fun isDroppedCategoryTitle(lowerPlainTitle: String): Boolean {
        return lowerPlainTitle.contains("leak") || lowerPlainTitle == "cam models"
    }

    private fun shouldDropEntryLine(line: String, droppedHosts: Set<String>): Boolean {
        if (containsBannedTerm(line)) return true
        val links = FmhyParser.extractInlineLinks(line)
        val primaryHost = links.firstOrNull()?.let { extractHttpHost(it.url) } ?: return false
        return primaryHost !in IGNORED_GENERAL_HOSTS && primaryHost in droppedHosts
    }

    private fun containsBannedTerm(rawLine: String): Boolean {
        val line = rawLine.filterNot { it.category == CharCategory.FORMAT }
        val rawLower = line.lowercase()
        if (BANNED_TERMS.any { rawLower.contains(it) }) return true
        val plainLower = FmhyParser.markdownToPlain(line).lowercase()
        if (BANNED_TERMS.any { plainLower.contains(it) }) return true
        val links = FmhyParser.extractInlineLinks(line)
        return links.any { link ->
            val labelLower = link.label.lowercase()
            val urlLower = link.url.lowercase()
            BANNED_TERMS.any { term -> labelLower.contains(term) || urlLower.contains(term) }
        }
    }

    private fun extractHttpHost(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return extractHost(trimmed)?.takeIf { it.isNotBlank() }
    }
}
