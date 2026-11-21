package io.github.omeg4dev.fmhy.data.parse

import java.security.MessageDigest
import java.util.Locale

object CriticalNotes {

    val JS_WS: Set<Char> = setOf(
        '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', '\u0020',
        '\u00A0', '\u1680',
        '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005',
        '\u2006', '\u2007', '\u2008', '\u2009', '\u200A',
        '\u2028', '\u2029',
        '\u202F', '\u205F', '\u3000',
        '\uFEFF'
    )

    private val FRONT_MATTER_RE = Regex("""^---\n([\s\S]*?)\n---\n?""")
    private val LINE_SPLIT_RE = Regex("""[\n\r\u2028\u2029]""")
    private val NOTE_LINK_RE = Regex("""\]\([^)\s]*\.vitepress/notes/([A-Za-z0-9_-]+)(?:\.md)?\)""")
    private val TITLE_RE = Regex("""^#+[\t\n\x0B\x0C\r \u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+(.*)$""")

    fun jsTrim(s: String): String = s.trim { it in JS_WS }

    fun contentHash(textMd: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(textMd.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }.substring(0, 12)
    }

    fun notePath(id: String): String = "docs/.vitepress/notes/$id.md"

    fun parseNote(id: String, raw: String): CriticalNoteRow? {
        val t = jsTrim(raw)
        var critical = id.endsWith("-critical")
        val frontMatterMatch = FRONT_MATTER_RE.find(t)
        val body = if (frontMatterMatch != null && frontMatterMatch.range.first == 0) {
            val group1 = frontMatterMatch.groupValues[1]
            val fmLines = group1.split(LINE_SPLIT_RE)
            var hasTrue = false
            var hasFalse = false
            for (line in fmLines) {
                if (line.startsWith("critical:")) {
                    val rest = jsTrim(line.substring("critical:".length))
                    if (rest == "true") {
                        hasTrue = true
                    } else if (rest == "false") {
                        hasFalse = true
                    }
                }
            }
            if (hasTrue) {
                critical = true
            } else if (hasFalse) {
                critical = false
            }
            jsTrim(t.substring(frontMatterMatch.value.length))
        } else {
            t
        }

        if (!critical) return null

        var title = ""
        val bodyLines = body.split(LINE_SPLIT_RE)
        for (line in bodyLines) {
            val m = TITLE_RE.matchEntire(line)
            if (m != null) {
                title = jsTrim(m.groupValues[1])
                break
            }
        }

        val hash = contentHash(body)
        return CriticalNoteRow(
            id = id,
            title = title,
            textMd = body,
            hash = hash,
        )
    }

    fun noteIdsInLine(line: String): List<String> {
        val results = LinkedHashSet<String>()
        for (match in NOTE_LINK_RE.findAll(line)) {
            results.add(match.groupValues[1])
        }
        return results.toList()
    }

    /** Distinct note ids of a line with the index of each id's first link, in order. */
    fun noteRefsInLine(line: String): List<Pair<String, Int>> {
        val results = LinkedHashMap<String, Int>()
        for (match in NOTE_LINK_RE.findAll(line)) {
            results.putIfAbsent(match.groupValues[1], match.range.first)
        }
        return results.map { it.key to it.value }
    }

    /**
     * Rows for note_links. Like the website, only links BEFORE the note link on a line are guarded: an entry gets
     * a row for a note only when its link (url or originalUrl, first occurrence in the line text) starts before
     * that note's first link on the line. An entry whose url is not found in the line text counts as before.
     */
    fun noteLinks(sectionId: String, markdown: String, entries: List<EntryRow>): List<NoteLinkRow> {
        val lines = markdown.lines()
        val refs = mutableMapOf<Int, List<Pair<String, Int>>>()
        for ((idx, line) in lines.withIndex()) {
            val found = noteRefsInLine(line)
            if (found.isNotEmpty()) {
                refs[idx + 1] = found
            }
        }

        val result = mutableListOf<NoteLinkRow>()
        for (entry in entries) {
            val noteRefs = refs[entry.line] ?: continue
            val text = lines[entry.line - 1]
            val first = listOfNotNull(entry.url, entry.originalUrl)
                .filter { it.isNotEmpty() }
                .map { text.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull()
            for ((ord, ref) in noteRefs.withIndex()) {
                if (first == null || first < ref.second) {
                    result.add(
                        NoteLinkRow(
                            entryId = entry.id,
                            noteId = ref.first,
                            sectionId = sectionId,
                            line = entry.line,
                            ord = ord,
                        )
                    )
                }
            }
        }
        return result.sortedWith(
            compareBy<NoteLinkRow> { it.sectionId }
                .thenBy { it.entryId }
                .thenBy { it.noteId }
        )
    }
}
