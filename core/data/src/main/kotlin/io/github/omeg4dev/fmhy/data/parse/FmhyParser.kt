package io.github.omeg4dev.fmhy.data.parse

data class ScannedLink(
    val label: String,
    val url: String,
    val startIndex: Int,
    val endIndex: Int
)

object FmhyParser {
    private val FRONT_MATTER_RE = Regex("""^---\s*$""")
    private val HEADING_RE = Regex("""^(#{1,4})\s+(.*)""")
    private val BULLET_RE = Regex("""^(\s*)([*-])\s+(.*)""")
    private val NOTE_MARKER_RE = Regex("""^\*\*Note\*\*\s*-\s*(.*)""")
    private val BLOCKQUOTE_RE = Regex("""^>\s*(.*)""")
    private val BACK_TO_TOP_RE = Regex("""^\[.*]\(#\S*\)\s*$""")

    fun parseSection(section: SectionInfo, markdown: String, b64: Base64Map): ParsedSection {
        val lines = markdown.lines().map { stripInvisibleChars(it) }
        val headings = mutableListOf<HeadingRow>()
        val entries = mutableListOf<EntryRow>()
        val notes = mutableListOf<NoteRow>()
        var unparsedLines = 0

        var inFrontMatter = false
        var inFence = false
        var ordCounter = 0
        var headingOrd = 0

        val anchorTracker = AnchorTracker()
        val headingStack = mutableListOf<HeadingRow>()
        val noteCounts = mutableMapOf<String, Int>()
        val idAssigner = IdAssigner()

        fun currentHeading(): HeadingRow? = headingStack.lastOrNull()

        fun currentHeadingPath(): String {
            val parts = mutableListOf(section.title)
            for (h in headingStack) {
                parts.add(h.title)
            }
            return parts.joinToString(" › ")
        }

        fun pushHeading(h: HeadingRow) {
            while (headingStack.isNotEmpty() && headingStack.last().level >= h.level) {
                headingStack.removeAt(headingStack.size - 1)
            }
            headingStack.add(h)
        }

        var i = 0
        if (lines.isNotEmpty() && FRONT_MATTER_RE.matches(lines[0])) {
            inFrontMatter = true
            i = 1
            while (i < lines.size) {
                if (FRONT_MATTER_RE.matches(lines[i])) {
                    inFrontMatter = false
                    i++
                    break
                }
                i++
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val lineNumber = i + 1
            i++

            if (line.startsWith(":::")) {
                inFence = !inFence
                continue
            }
            if (inFence) continue

            if (line.trimStart().startsWith("<")) continue

            val trimmedLine = line.trim()
            if (trimmedLine == "***" || trimmedLine == "---") continue

            if (line.contains("back to wiki index", ignoreCase = true)) continue

            if (BACK_TO_TOP_RE.matches(trimmedLine)) continue

            val headingMatch = HEADING_RE.matchEntire(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val rawTitle = headingMatch.groupValues[2].trim()
                val displayTitle = rawTitle.replace("►", "").replace("▷", "").trim()
                val anchor = Slug.slugify(rawTitle)
                val uniqueAnchor = anchorTracker.add(anchor)
                val parent = headingStack.lastOrNull { it.level < level }

                val h = HeadingRow(
                    id = "${section.id}/$uniqueAnchor",
                    sectionId = section.id,
                    parentId = parent?.id,
                    level = level,
                    title = displayTitle,
                    anchor = uniqueAnchor,
                    ord = headingOrd++
                )
                pushHeading(h)
                headings.add(h)
                continue
            }

            val bqMatch = BLOCKQUOTE_RE.matchEntire(line)
            if (bqMatch != null) {
                val text = bqMatch.groupValues[1].trim()
                val ch = currentHeading()
                val parentKey = ch?.id ?: section.id
                val k = (noteCounts[parentKey] ?: 0) + 1
                noteCounts[parentKey] = k
                val noteId = "$parentKey#n$k"

                notes.add(
                    NoteRow(
                        id = noteId,
                        sectionId = section.id,
                        headingId = ch?.id,
                        ord = ordCounter++,
                        textMd = text
                    )
                )
                continue
            }

            val bulletMatch = BULLET_RE.matchEntire(line)
            if (bulletMatch != null) {
                val bulletContent = bulletMatch.groupValues[3]

                val noteMatch = NOTE_MARKER_RE.matchEntire(bulletContent)
                if (noteMatch != null) {
                    val text = noteMatch.groupValues[1].trim()
                    val ch = currentHeading()
                    val parentKey = ch?.id ?: section.id
                    val k = (noteCounts[parentKey] ?: 0) + 1
                    noteCounts[parentKey] = k
                    val noteId = "$parentKey#n$k"

                    notes.add(
                        NoteRow(
                            id = noteId,
                            sectionId = section.id,
                            headingId = ch?.id,
                            ord = ordCounter++,
                            textMd = text
                        )
                    )
                    continue
                }

                val parsed = parseBulletLine(
                    bulletContent,
                    sectionId = section.id,
                    headingId = currentHeading()?.id,
                    headingPath = currentHeadingPath(),
                    isInlineMore = false,
                    line = lineNumber
                )

                if (parsed == null || parsed.isEmpty()) {
                    unparsedLines++
                    continue
                }

                for (p in parsed) {
                    val eid = idAssigner.assign(Ids.normalizeUrl(p.url), section.id)
                    val (resolvedUrl, originalUrl, allMirrors) = b64.resolve(p.url, p.mirrors)
                    entries.add(
                        EntryRow(
                            id = eid,
                            sectionId = section.id,
                            headingId = p.headingId,
                            ord = ordCounter++,
                            name = p.name,
                            url = resolvedUrl,
                            originalUrl = originalUrl,
                            description = p.description,
                            tags = p.tags,
                            headingPath = p.headingPath,
                            tier = p.tier,
                            kind = p.kind,
                            line = p.line,
                            isInlineMore = p.isInlineMore,
                            isBase64 = originalUrl != null,
                            mirrors = allMirrors,
                            extraLinks = p.extraLinks
                        )
                    )
                }
                continue
            }

            val stripped = line.trim()
            if (stripped.isNotEmpty()) {
                val links = extractInlineLinks(stripped)
                if (links.isNotEmpty()) {
                    val parsed = parseBulletLine(
                        stripped,
                        sectionId = section.id,
                        headingId = currentHeading()?.id,
                        headingPath = currentHeadingPath(),
                        isInlineMore = true,
                        line = lineNumber
                    )
                    if (!parsed.isNullOrEmpty()) {
                        for (p in parsed) {
                            val eid = idAssigner.assign(Ids.normalizeUrl(p.url), section.id)
                            val (resolvedUrl, originalUrl, allMirrors) = b64.resolve(p.url, p.mirrors)
                            entries.add(
                                EntryRow(
                                    id = eid,
                                    sectionId = section.id,
                                    headingId = p.headingId,
                                    ord = ordCounter++,
                                    name = p.name,
                                    url = resolvedUrl,
                                    originalUrl = originalUrl,
                                    description = p.description,
                                    tags = p.tags,
                                    headingPath = p.headingPath,
                                    tier = p.tier,
                                    kind = p.kind,
                                    line = p.line,
                                    isInlineMore = p.isInlineMore,
                                    isBase64 = originalUrl != null,
                                    mirrors = allMirrors,
                                    extraLinks = p.extraLinks
                                )
                            )
                        }
                    } else {
                        unparsedLines++
                    }
                }
            }
        }

        return ParsedSection(section, headings, entries, notes, unparsedLines)
    }

    fun stripInvisibleChars(text: String): String {
        if (!text.any { it == '\u2060' || it == '\u200B' || it == '\u200C' || it == '\uFEFF' }) {
            return text
        }
        val sb = StringBuilder(text.length)
        for (i in 0 until text.length) {
            val c = text[i]
            if (c != '\u2060' && c != '\u200B' && c != '\u200C' && c != '\uFEFF') {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private val SOCIAL_LABELS = setOf(
        "discord", "github", "gitlab", "source code", "telegram",
        "subreddit", "x", "mastodon", "bluesky", ".onion", "status"
    )

    fun cleanDanglingSeparators(s: String): String {
        var str = s.trim()
        var changed = true
        while (changed && str.isNotEmpty()) {
            changed = false
            str = str.trim()
            if (str.endsWith(",") || str.endsWith("/")) {
                str = str.dropLast(1).trim()
                changed = true
            }
            if (str.startsWith(",") || str.startsWith("/")) {
                str = str.drop(1).trim()
                changed = true
            }
        }
        str = str.replace(Regex("""\s+"""), " ")
        return str
    }

    private data class PartialEntry(
        val name: String,
        val url: String,
        val tier: Int,
        var kind: Int = 0,
        val line: Int = 0,
        val isInlineMore: Boolean,
        val headingId: String?,
        val headingPath: String,
        var description: String = "",
        var tags: String = "",
        val mirrors: MutableList<LinkRow> = mutableListOf(),
        val extraLinks: MutableList<LinkRow> = mutableListOf()
    )

    private fun parseBulletLine(
        content: String,
        sectionId: String,
        headingId: String?,
        headingPath: String,
        isInlineMore: Boolean,
        line: Int
    ): List<PartialEntry>? {
        val cleaned = stripInvisibleChars(content)
        val (tier, kind, withoutTierAndKind) = detectTierAndKind(cleaned)
        val (head, desc) = splitHeadDesc(withoutTierAndKind)

        val headLinks = extractInlineLinks(head)
        if (headLinks.isEmpty()) {
            val descLinks = extractInlineLinks(desc)
            if (descLinks.isEmpty()) {
                return null
            }
            val rawName = head.replace("**", "").trim()
            val name = stripLeadingEmoji(rawName).trim()
            if (name.isEmpty()) {
                return null
            }
            val firstUrl = descLinks[0].url
            val extraLinks = descLinks.map { LinkRow(it.label, it.url) }.toMutableList()
            val (_, nonLinkDesc) = splitHeadDesc(desc)
            val description = if (nonLinkDesc.isNotEmpty()) markdownToPlain(nonLinkDesc).trim() else ""
            val tags = description

            return listOf(
                PartialEntry(
                    name = name,
                    url = firstUrl,
                    tier = tier,
                    kind = if (isInlineMore) 0 else kind,
                    line = line,
                    isInlineMore = isInlineMore,
                    headingId = headingId,
                    headingPath = headingPath,
                    description = description,
                    tags = tags,
                    mirrors = mutableListOf(),
                    extraLinks = extraLinks
                )
            )
        }

        val lineKind = if (isInlineMore) 0 else kind
        val entryCandidates = parseHeadLinks(head, tier, lineKind, line, isInlineMore, headingId, headingPath)
        if (entryCandidates.isEmpty()) {
            return null
        }

        val (description, tags, extraLinks) = parseDesc(desc)
        for (i in entryCandidates.indices) {
            val e = entryCandidates[i]
            e.description = description
            e.tags = tags
            if (i == 0) {
                e.extraLinks.addAll(extraLinks)
            }
        }

        return entryCandidates
    }

    fun detectTierAndKind(line: String): Triple<Int, Int, String> {
        var tier = 0
        var kind = 0
        var s = line.trimStart()

        for (step in 0 until 2) {
            s = s.trimStart()
            if (tier == 0) {
                if (s.startsWith("🌟")) {
                    tier = 2
                    s = s.substring("🌟".length)
                    continue
                } else if (s.startsWith("⭐")) {
                    tier = 1
                    s = s.substring("⭐".length)
                    continue
                }
            }
            if (kind == 0) {
                if (s.startsWith("🌐")) {
                    kind = 1
                    s = s.substring("🌐".length)
                    if (s.startsWith("\uFE0F")) s = s.substring(1)
                    continue
                } else if (s.startsWith("↪️")) {
                    kind = 2
                    s = s.substring("↪️".length)
                    continue
                } else if (s.startsWith("↪")) {
                    kind = 2
                    s = s.substring("↪".length)
                    if (s.startsWith("\uFE0F")) s = s.substring(1)
                    continue
                }
            }
        }
        return Triple(tier, kind, s.trimStart())
    }

    fun detectTier(line: String): Pair<Int, String> {
        val (tier, _, rest) = detectTierAndKind(line)
        return tier to rest
    }

    fun splitHeadDesc(line: String): Pair<String, String> {
        var depth = 0
        var i = 0
        val n = line.length
        var inLinkUrl = false

        while (i < n) {
            val ch = line[i]
            if (ch == '[') {
                depth++
            } else if (ch == ']') {
                depth--
                if (depth < 0) depth = 0
                if (i + 1 < n && line[i + 1] == '(') {
                    inLinkUrl = true
                    i += 2
                    while (i < n && line[i] != ')') {
                        i++
                    }
                    inLinkUrl = false
                    if (i < n) {
                        i++
                    }
                    continue
                }
            }

            if (depth == 0 && !inLinkUrl) {
                if (i + 3 <= n && line.startsWith(" - ", i)) {
                    return line.substring(0, i) to line.substring(i + 3)
                }
            }
            i++
        }
        return line to ""
    }

    private fun parseHeadLinks(
        headText: String,
        tier: Int,
        lineKind: Int,
        line: Int,
        isInlineMore: Boolean,
        headingId: String?,
        headingPath: String
    ): List<PartialEntry> {
        val entries = mutableListOf<PartialEntry>()
        var current: PartialEntry? = null
        var inBold = false
        val prevTextTail = StringBuilder()

        fun finishEntry() {
            current?.let { entries.add(it) }
            current = null
        }

        fun newEntry(name: String, url: String, linkTier: Int, linkKind: Int) {
            finishEntry()
            current = PartialEntry(
                name = stripLeadingEmoji(name).trim(),
                url = url,
                tier = linkTier,
                kind = linkKind,
                line = line,
                isInlineMore = isInlineMore,
                headingId = headingId,
                headingPath = headingPath
            )
        }

        var i = 0
        val n = headText.length
        while (i < n) {
            if (i + 1 < n && headText[i] == '*' && headText[i + 1] == '*') {
                inBold = !inBold
                i += 2
                continue
            }

            val link = scanLinkAt(headText, i)
            if (link != null) {
                val sepText = prevTextTail.toString()
                prevTextTail.setLength(0)

                if (isNumberLabel(link.label) && current != null) {
                    current!!.mirrors.add(LinkRow(link.label, link.url))
                } else {
                    val isExtra = (" / " in sepText || sepText.trim() == "/") && current != null
                    if (isExtra) {
                        current!!.extraLinks.add(LinkRow(link.label, link.url))
                    } else {
                        val isFirstEntry = entries.isEmpty() && current == null
                        val linkTier = tier
                        val linkKind = when {
                            link.label.trimStart().startsWith("🌐") || sepText.contains("🌐") -> 1
                            link.label.trimStart().startsWith("↪") || sepText.contains("↪") -> 2
                            isFirstEntry -> lineKind
                            else -> 0
                        }
                        newEntry(link.label, link.url, linkTier, linkKind)
                    }
                }
                i = link.endIndex
                continue
            }

            prevTextTail.append(headText[i])
            i++
        }

        finishEntry()
        return entries
    }

    private fun parseDesc(descText: String): Triple<String, String, List<LinkRow>> {
        val trimmed = descText.trim()
        if (trimmed.isEmpty()) {
            return Triple("", "", emptyList())
        }

        val parts = splitOutsideLinks(trimmed, " / ")
        val tagParts = mutableListOf<String>()
        val extraLinks = mutableListOf<LinkRow>()

        for (part in parts) {
            val partTrimmed = part.trim()
            if (partTrimmed.isEmpty()) continue

            if (!partTrimmed.contains('[')) {
                tagParts.add(partTrimmed)
                continue
            }

            val links = extractInlineLinks(partTrimmed)
            if (links.size == 1 && isSoleLink(partTrimmed, links[0])) {
                extraLinks.add(LinkRow(links[0].label, links[0].url))
            } else {
                val socialLinks = links.filter { it.label.trim().lowercase() in SOCIAL_LABELS }
                if (socialLinks.isNotEmpty()) {
                    extraLinks.addAll(socialLinks.map { LinkRow(it.label, it.url) })
                    var currentText = partTrimmed
                    for (sl in socialLinks.sortedByDescending { it.startIndex }) {
                        currentText = currentText.substring(0, sl.startIndex) + currentText.substring(sl.endIndex)
                    }
                    val cleaned = cleanDanglingSeparators(currentText)
                    if (cleaned.isNotEmpty()) {
                        tagParts.add(markdownToPlain(cleaned))
                    }
                } else {
                    tagParts.add(markdownToPlain(partTrimmed))
                }
            }
        }

        val tags = tagParts.joinToString("|")
        val description = tagParts.joinToString(" / ")
        return Triple(description, tags, extraLinks)
    }

    fun splitOutsideLinks(text: String, sep: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        var i = 0
        val n = text.length
        val sepLen = sep.length

        while (i < n) {
            val ch = text[i]
            if (ch == '[') {
                depth++
                current.append(ch)
                i++
            } else if (ch == ']') {
                depth--
                if (depth < 0) depth = 0
                current.append(ch)
                if (i + 1 < n && text[i + 1] == '(') {
                    i++
                    current.append(text[i])
                    i++
                    while (i < n && text[i] != ')') {
                        current.append(text[i])
                        i++
                    }
                    if (i < n) {
                        current.append(text[i])
                        i++
                    }
                } else {
                    i++
                }
            } else if (depth == 0 && i + sepLen <= n && text.startsWith(sep, i)) {
                parts.add(current.toString())
                current.setLength(0)
                i += sepLen
            } else {
                current.append(ch)
                i++
            }
        }
        parts.add(current.toString())
        return parts
    }

    private fun isSoleLink(text: String, link: ScannedLink): Boolean {
        val stripped = text.trim()
        val expected = "[${link.label}](${link.url})"
        return stripped == expected
    }

    fun markdownToPlain(text: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val link = scanLinkAt(text, i)
            if (link != null) {
                sb.append(link.label)
                i = link.endIndex
            } else {
                val ch = text[i]
                if (ch == '*' || ch == '`') {
                    i++
                } else {
                    sb.append(ch)
                    i++
                }
            }
        }
        return sb.toString()
    }

    fun extractInlineLinks(text: String): List<ScannedLink> {
        val links = mutableListOf<ScannedLink>()
        var i = 0
        val n = text.length
        while (i < n) {
            val link = scanLinkAt(text, i)
            if (link != null) {
                links.add(link)
                i = link.endIndex
            } else {
                i++
            }
        }
        return links
    }

    fun isNumberLabel(label: String): Boolean {
        if (label.isEmpty() || !label.all { it.isDigit() }) return false
        val num = label.toLongOrNull() ?: return false
        return num >= 2
    }

    fun scanLinkAt(text: String, startIndex: Int): ScannedLink? {
        if (startIndex >= text.length || text[startIndex] != '[') return null

        if (startIndex > 0 && text[startIndex - 1] == '!') {
            var backslashCount = 0
            var b = startIndex - 2
            while (b >= 0 && text[b] == '\\') {
                backslashCount++
                b--
            }
            if (backslashCount % 2 == 0) {
                // It is an unescaped image ![...]
                return null
            }
        }

        var backslashCount = 0
        var b = startIndex - 1
        while (b >= 0 && text[b] == '\\') {
            backslashCount++
            b--
        }
        if (backslashCount % 2 != 0) {
            // Escaped \[
            return null
        }

        var depth = 1
        var j = startIndex + 1
        val rawLabel = StringBuilder()
        while (j < text.length && depth > 0) {
            val c = text[j]
            if (c == '\\' && j + 1 < text.length) {
                rawLabel.append(c)
                rawLabel.append(text[j + 1])
                j += 2
                continue
            }
            if (c == '[') {
                depth++
                rawLabel.append(c)
                j++
            } else if (c == ']') {
                depth--
                if (depth == 0) {
                    j++ // past ']'
                    break
                } else {
                    rawLabel.append(c)
                    j++
                }
            } else {
                rawLabel.append(c)
                j++
            }
        }

        if (depth != 0) return null

        if (j >= text.length || text[j] != '(') return null
        j++ // past '('

        while (j < text.length && text[j].isWhitespace()) j++
        if (j >= text.length) return null

        if (text[j] == '<') {
            j++ // skip '<'
            val urlSb = StringBuilder()
            while (j < text.length && text[j] != '>') {
                if (text[j] == '\\' && j + 1 < text.length) {
                    urlSb.append(text[j + 1])
                    j += 2
                } else {
                    urlSb.append(text[j])
                    j++
                }
            }
            if (j >= text.length || text[j] != '>') return null
            j++ // skip '>'

            while (j < text.length && text[j].isWhitespace()) j++
            if (j < text.length && (text[j] == '"' || text[j] == '\'' || text[j] == '(')) {
                val quote = if (text[j] == '(') ')' else text[j]
                j++
                while (j < text.length && text[j] != quote) {
                    if (text[j] == '\\' && j + 1 < text.length) j += 2 else j++
                }
                if (j < text.length) j++
                while (j < text.length && text[j].isWhitespace()) j++
            }
            if (j >= text.length || text[j] != ')') return null
            j++ // skip ')'
            val label = cleanLabel(rawLabel.toString())
            return ScannedLink(label, cleanUrl(urlSb.toString()), startIndex, j)
        } else {
            var parenDepth = 1
            val urlSb = StringBuilder()
            var inTitle = false
            var titleQuote: Char? = null

            while (j < text.length) {
                val c = text[j]
                if (c == '\\' && j + 1 < text.length) {
                    if (!inTitle) urlSb.append(text[j + 1])
                    j += 2
                    continue
                }
                if (inTitle) {
                    if (c == titleQuote) {
                        inTitle = false
                        titleQuote = null
                    }
                    j++
                    continue
                }
                if (c == '(') {
                    parenDepth++
                    urlSb.append(c)
                    j++
                } else if (c == ')') {
                    parenDepth--
                    if (parenDepth == 0) {
                        j++ // skip ')'
                        break
                    } else {
                        urlSb.append(c)
                        j++
                    }
                } else if (c.isWhitespace()) {
                    if (parenDepth == 1) {
                        j++
                        while (j < text.length && text[j].isWhitespace()) j++
                        if (j < text.length && (text[j] == '"' || text[j] == '\'' || text[j] == '(')) {
                            inTitle = true
                            titleQuote = if (text[j] == '(') ')' else text[j]
                            j++
                        } else if (j < text.length && text[j] == ')') {
                            parenDepth = 0
                            j++ // skip ')'
                            break
                        }
                    } else {
                        urlSb.append(c)
                        j++
                    }
                } else {
                    urlSb.append(c)
                    j++
                }
            }

            if (parenDepth != 0) return null
            val label = cleanLabel(rawLabel.toString())
            return ScannedLink(label, cleanUrl(urlSb.toString()), startIndex, j)
        }
    }

    private fun cleanUrl(raw: String): String {
        return raw.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun cleanLabel(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                if (next == '[' || next == ']' || next == '\\') {
                    sb.append(next)
                    i += 2
                    continue
                }
            }
            sb.append(raw[i])
            i++
        }
        return sb.toString()
    }

    fun stripLeadingEmoji(text: String): String {
        return Slug.stripTitlePrefix(text)
    }
}
