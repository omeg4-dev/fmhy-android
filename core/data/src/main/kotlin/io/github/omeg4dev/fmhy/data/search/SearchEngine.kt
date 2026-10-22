package io.github.omeg4dev.fmhy.data.search

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import java.text.Normalizer
import kotlinx.serialization.json.Json

open class SearchEngine(private val store: DatasetStore) {

    private companion object {
        /** Categories computed per search; the UI shows the best few and expands the rest on demand. */
        const val CATEGORY_LIMIT = 50
    }

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun search(
        query: String,
        sectionFilter: Set<String> = emptySet(),
        limit: Int = 200,
        mode: MatchMode = MatchMode.FUZZY,
    ): SearchResults = search(QueryParser.parse(query), sectionFilter, limit, mode)

    open suspend fun search(
        query: Query,
        sectionFilter: Set<String> = emptySet(),
        limit: Int = 200,
        mode: MatchMode = MatchMode.FUZZY,
    ): SearchResults {
        val startTime = System.currentTimeMillis()

        val allSections = store.sections()
        val sectionsById = allSections.associateBy { it.id }

        // Resolve in:<prefix> from query against section id and title
        val querySectionIds = if (query.inSections.isNotEmpty()) {
            val matched = mutableSetOf<String>()
            for (pattern in query.inSections) matched.addAll(QueryEdit.resolveSections(pattern, allSections))
            matched
        } else {
            emptySet()
        }

        // Intersect or select effective section filter
        val effectiveSections = when {
            sectionFilter.isNotEmpty() && querySectionIds.isNotEmpty() -> sectionFilter.intersect(querySectionIds)
            sectionFilter.isNotEmpty() -> sectionFilter
            querySectionIds.isNotEmpty() -> querySectionIds
            else -> emptySet()
        }

        if ((sectionFilter.isNotEmpty() || query.inSections.isNotEmpty()) && effectiveSections.isEmpty()) {
            return SearchResults(emptyList(), 0, System.currentTimeMillis() - startTime)
        }

        val hasPositiveTerms = query.words.isNotEmpty() || query.phrases.isNotEmpty()

        if (!hasPositiveTerms && query.isEmpty && effectiveSections.isEmpty()) {
            return SearchResults(emptyList(), 0, System.currentTimeMillis() - startTime)
        }

        val allHits = if (hasPositiveTerms) {
            searchFts(query, effectiveSections, limit, mode)
        } else {
            searchPlain(query, effectiveSections, limit)
        }

        val groupsMap = LinkedHashMap<String, MutableList<SearchHit>>()
        for (hit in allHits) {
            groupsMap.getOrPut(hit.entry.sectionId) { mutableListOf() }.add(hit)
        }

        val groups = groupsMap.map { (secId, hits) ->
            val sec = sectionsById[secId] ?: Section(secId, secId, "", 0, 0)
            SearchGroup(sec, hits)
        }

        val categories = if (query.words.isNotEmpty()) searchCategories(query, CATEGORY_LIMIT) else emptyList()

        val tookMs = System.currentTimeMillis() - startTime
        return SearchResults(groups, allHits.size, tookMs, categories)
    }

    private class CategoryIndexEntry(
        val hit: CategoryHit,
        val normTitle: String,
        val titleWords: List<String>,
        val level: Int,
        val sectionOrd: Int,
        val headingOrd: Int,
    )

    private var categoryIndexVersion: Long? = null
    private var categoryIndex: List<CategoryIndexEntry> = emptyList()

    private suspend fun categoryIndexFor(): List<CategoryIndexEntry> {
        val version = store.version()
        synchronized(this) {
            if (categoryIndexVersion == version) return categoryIndex
        }
        val sections = store.sections()
        val headings = store.allHeadings()
        val sectionsById = sections.associateBy { it.id }
        val headingsById = headings.associateBy { it.id }
        val built = ArrayList<CategoryIndexEntry>(sections.size + headings.size)
        for (sec in sections) {
            if (sec.title.isBlank()) continue
            val n = normalizeForSearch(sec.title)
            built.add(CategoryIndexEntry(CategoryHit(sec, null, emptyList()), n, n.splitWords(), 0, sec.ord, 0))
        }
        for (h in headings) {
            if (h.title.isBlank()) continue
            val sec = sectionsById[h.sectionId] ?: continue
            val ancestors = ArrayList<String>()
            var parent = h.parentId?.let { headingsById[it] }
            var guard = 0
            while (parent != null && guard++ < 8) {
                ancestors.add(0, parent.title)
                parent = parent.parentId?.let { headingsById[it] }
            }
            val n = normalizeForSearch(h.title)
            built.add(
                CategoryIndexEntry(
                    CategoryHit(sec, h, listOf(sec.title) + ancestors),
                    n, n.splitWords(), h.level, sec.ord, h.ord,
                )
            )
        }
        synchronized(this) {
            categoryIndex = built
            categoryIndexVersion = version
        }
        return built
    }

    /** Sections and headings whose title matches the plain words of [query] (operators are ignored). */
    open suspend fun searchCategories(query: Query, limit: Int = 6): List<CategoryHit> {
        val words = query.words.map { normalizeForSearch(it) }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val q = words.joinToString(" ")
        val ranked = ArrayList<Pair<Int, CategoryIndexEntry>>()
        for (e in categoryIndexFor()) {
            val rank = when {
                e.normTitle == q -> 0
                e.normTitle.startsWith(q) -> 1
                words.all { w -> e.titleWords.any { it.startsWith(w) } } -> 2
                e.normTitle.contains(q) -> 3
                else -> continue
            }
            ranked.add(rank to e)
        }
        return ranked
            .sortedWith(
                compareBy<Pair<Int, CategoryIndexEntry>>({ it.first }, { it.second.level }, { it.second.sectionOrd }, { it.second.headingOrd })
            )
            .take(limit)
            .map { it.second.hit }
    }

    private var vocabularyVersion: Long? = null
    private var vocabulary: Map<String, Int> = emptyMap()
    private var vocabularySorted: Array<String> = emptyArray()

    private suspend fun vocabularyFor(): Map<String, Int> {
        val version = store.version()
        synchronized(this) {
            if (vocabularyVersion == version) return vocabulary
        }
        val counts = HashMap<String, Int>()
        fun addTokens(text: String) {
            for (t in text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))) {
                if (t.length >= 3) counts[t] = (counts[t] ?: 0) + 1
            }
        }
        store.withConnection { conn ->
            conn.prepare("SELECT name FROM entries").use { stmt ->
                while (stmt.step()) addTokens(stmt.getText(0))
            }
        }
        for (h in store.allHeadings()) addTokens(h.title)
        synchronized(this) {
            vocabulary = counts
            vocabularySorted = counts.keys.sorted().toTypedArray()
            vocabularyVersion = version
        }
        return counts
    }

    /** "Did you mean" for a query with no hits: each plain word of 4+ characters is replaced by the closest
     *  known term (Damerau-Levenshtein, at most 1 edit for 4-5 letters, 2 for longer; more frequent term wins
     *  ties). Returns the whole query with the corrections applied, or null when nothing changed. */
    open suspend fun suggest(query: Query): String? {
        val candidates = query.words.map { it.lowercase() }.filter { it.length >= 4 }.distinct()
        if (candidates.isEmpty()) return null
        val vocab = vocabularyFor()
        val fixes = HashMap<String, String>()
        for (word in candidates) {
            if (vocab.containsKey(word)) continue
            val maxDist = if (word.length <= 5) 1 else 2
            var best: String? = null
            var bestDist = Int.MAX_VALUE
            var bestFreq = -1
            for ((term, freq) in vocab) {
                if (kotlin.math.abs(term.length - word.length) > maxDist) continue
                val d = editDistance(word, term, maxDist)
                if (d > maxDist) continue
                if (d < bestDist || (d == bestDist && freq > bestFreq)) {
                    best = term; bestDist = d; bestFreq = freq
                }
            }
            if (best != null) fixes[word] = best
        }
        if (fixes.isEmpty()) return null
        val result = query.raw.trim().split(Regex("\\s+")).joinToString(" ") { tok -> fixes[tok.lowercase()] ?: tok }
        return result.takeIf { !it.equals(query.raw.trim(), ignoreCase = false) }
    }

    /** Optimal string alignment distance (Damerau-Levenshtein with adjacent transpositions). */
    private fun editDistance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        val n = a.length
        val m = b.length
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
            }
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    private var hostIndexVersion: Long? = null
    private var hostIndex: List<Pair<String, Int>> = emptyList()

    private suspend fun hostIndexFor(): List<Pair<String, Int>> {
        val version = store.version()
        synchronized(this) {
            if (hostIndexVersion == version) return hostIndex
        }
        val counts = HashMap<String, Int>()
        store.withConnection { conn ->
            conn.prepare("SELECT url FROM entries").use { stmt ->
                while (stmt.step()) {
                    val host = hostOf(stmt.getText(0)) ?: continue
                    counts[host] = (counts[host] ?: 0) + 1
                }
            }
        }
        val built = counts.entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key to it.value }
        synchronized(this) {
            hostIndex = built
            hostIndexVersion = version
        }
        return built
    }

    private fun hostOf(url: String): String? {
        val rest = url.substringAfter("://", "")
        if (rest.isEmpty()) return null
        val host = rest.takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }.lowercase().removePrefix("www.")
        return host.takeIf { it.contains('.') && !it.contains('@') }
    }

    /** Hosts of the dataset's links that start with (or, failing that, contain) [prefix], most used first. */
    open suspend fun suggestHosts(prefix: String, limit: Int = 8): List<String> {
        val p = prefix.trim().lowercase().removePrefix("www.")
        val all = try { hostIndexFor() } catch (_: Exception) { return emptyList() }
        if (p.isEmpty()) return all.take(limit).map { it.first }
        val starts = all.filter { it.first.startsWith(p) }
        val rest = if (starts.size < limit) all.filter { !it.first.startsWith(p) && it.first.contains(p) } else emptyList()
        return (starts + rest).take(limit).map { it.first }
    }

    private fun String.splitWords(): List<String> = split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun normalizeForSearch(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

    private fun ftsPhrase(s: String) = "\"${s.replace("\"", "\"\"")}\""

    /** Words of 4+ letters that are neither a vocabulary prefix nor a known word get up to three close terms
     *  (Damerau-Levenshtein, 1 edit up to 5 letters, 2 beyond). Keys are the lower-cased typed words. */
    private suspend fun typoCorrections(query: Query): Map<String, List<String>> {
        val candidates = query.words.filter { w -> w.length >= 4 && w.all { it.isLetterOrDigit() } }
            .map { it.lowercase() }.distinct()
        if (candidates.isEmpty()) return emptyMap()
        val vocab = vocabularyFor()
        val sorted = synchronized(this) { vocabularySorted }
        val out = LinkedHashMap<String, List<String>>()
        for (word in candidates) {
            var idx = java.util.Arrays.binarySearch(sorted, word)
            if (idx < 0) idx = -idx - 1
            if (idx < sorted.size && sorted[idx].startsWith(word)) continue // a real prefix: nothing to fix
            val maxDist = if (word.length <= 5) 1 else 2
            val found = ArrayList<Triple<Int, Int, String>>()
            for ((term, freq) in vocab) {
                if (kotlin.math.abs(term.length - word.length) > maxDist) continue
                val d = editDistance(word, term, maxDist)
                if (d <= maxDist) found.add(Triple(d, freq, term))
            }
            if (found.isNotEmpty()) {
                out[word] = found.sortedWith(compareBy<Triple<Int, Int, String>>({ it.first }, { -it.second })).take(3).map { it.third }
            }
        }
        return out
    }

    private suspend fun searchFts(
        query: Query,
        effectiveSections: Set<String>,
        limit: Int,
        mode: MatchMode,
    ): List<SearchHit> {
        val exact = mode == MatchMode.EXACT
        val primaryTerm = query.words.firstOrNull() ?: query.phrases.firstOrNull() ?: ""
        val hits = ArrayList<SearchHit>()
        val seen = HashSet<String>()
        fun addAll(list: List<SearchHit>) {
            for (h in list) if (hits.size < limit && seen.add(h.entry.id)) hits.add(h)
        }

        fun wordTerm(w: String) = if (exact) ftsPhrase(w) else ftsPhrase(w) + "*"
        val phraseTerms = query.phrases.map { ftsPhrase(it) }
        val excludeTerms = query.excludes.map { wordTerm(it) }
        fun expr(wordTerms: List<String>): String {
            val positive = wordTerms + phraseTerms
            return if (excludeTerms.isNotEmpty()) positive.joinToString(" ") + " NOT " + excludeTerms.joinToString(" NOT ")
            else positive.joinToString(" ")
        }

        // 1. Words (prefix matches unless exact) and phrases.
        addAll(runFts(query, effectiveSections, limit, expr(query.words.map { wordTerm(it) }), primaryTerm))
        if (exact) return hits

        // 2. Typo corrections, ranked after the direct hits.
        val fixes = typoCorrections(query)
        if (fixes.isNotEmpty() && hits.size < limit) {
            val terms = query.words.map { w ->
                val alts = fixes[w.lowercase()]
                if (alts == null) wordTerm(w) else alts.joinToString(" OR ", "(", ")") { wordTerm(it) }
            }
            addAll(runFts(query, effectiveSections, limit - hits.size, expr(terms), primaryTerm))
        }

        // 3. Substring (trigram) matches of the longest word, after the word hits.
        val allWords = query.words + query.phrases.flatMap { it.split("\\s+".toRegex()) }
        val longestWord = allWords.maxByOrNull { it.length } ?: ""
        if (longestWord.length >= 3 && hits.size < limit) {
            addAll(searchTrigram(longestWord, allWords - longestWord, query, effectiveSections, limit - hits.size))
        }
        return hits
    }

    private suspend fun runFts(
        query: Query,
        effectiveSections: Set<String>,
        limit: Int,
        matchExpr: String,
        primaryTerm: String,
    ): List<SearchHit> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions.add("fts_words MATCH ?")
        params.add(matchExpr)

        if (query.isStarred) conditions.add("e.tier >= 1")
        if (query.isTop) conditions.add("e.tier = 2")
        if (query.isB64) conditions.add("e.is_base64 = 1")
        if (query.site != null) {
            conditions.add("(e.url LIKE '%://' || ? || '/%' OR e.url LIKE '%://' || ? OR e.url LIKE '%.' || ? || '/%' OR e.url LIKE '%.' || ?)")
            params.add(query.site)
            params.add(query.site)
            params.add(query.site)
            params.add(query.site)
        }
        if (effectiveSections.isNotEmpty()) {
            val placeholders = effectiveSections.joinToString(",") { "?" }
            conditions.add("e.section_id IN ($placeholders)")
            effectiveSections.forEach { params.add(it) }
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        // fts_words columns: name 0, tags 1, description 2, heading_path 3, url 4.
        val sql = """
            SELECT
                e.num, e.id, e.section_id, e.heading_id, e.ord, e.name, e.url, e.original_url,
                e.description, e.tags, e.heading_path, e.tier, e.kind, e.line, e.is_inline_more, e.is_base64,
                e.mirrors, e.extra_links, e.first_seen, e.last_changed, e.starred_at,
                highlight(fts_words, 0, char(2), char(3)) AS name_hl,
                highlight(fts_words, 2, char(2), char(3)) AS desc_hl,
                highlight(fts_words, 4, char(2), char(3)) AS url_hl,
                highlight(fts_words, 3, char(2), char(3)) AS path_hl,
                highlight(fts_words, 1, char(2), char(3)) AS tags_hl,
                (bm25(fts_words, 10.0, 4.0, 3.0, 2.0, 1.0)
                  - (CASE WHEN e.tier = 2 THEN 3.0 WHEN e.tier = 1 THEN 1.5 ELSE 0.0 END)
                  - (CASE WHEN lower(e.name) = lower(?) THEN 5.0 WHEN lower(e.name) LIKE lower(?) || '%' THEN 2.0 ELSE 0.0 END)
                ) AS rank_score
            FROM fts_words
            JOIN entries e ON fts_words.rowid = e.num
            $whereClause
            ORDER BY rank_score ASC
            LIMIT ?
        """.trimIndent()

        val hits = mutableListOf<SearchHit>()
        store.withConnection { conn ->
            conn.prepare(sql).use { stmt ->
                var idx = 1
                stmt.bindText(idx++, primaryTerm)
                stmt.bindText(idx++, primaryTerm)
                for (p in params) {
                    when (p) {
                        is String -> stmt.bindText(idx++, p)
                        is Long -> stmt.bindLong(idx++, p)
                        is Int -> stmt.bindLong(idx++, p.toLong())
                    }
                }
                stmt.bindLong(idx, limit.toLong())

                while (stmt.step()) {
                    val entry = readEntry(stmt)
                    val nameHl = stmt.getText(21)
                    val descHl = stmt.getText(22)
                    val extra = extraMatch(nameHl, descHl, stmt.getText(23), stmt.getText(24), stmt.getText(25))
                    hits.add(SearchHit(entry, nameHl, descHl, extra?.first, extra?.second))
                }
            }
        }
        return hits
    }

    /** Where a match sits when neither the name nor the description (the visible columns) contains it. */
    private fun extraMatch(nameHl: String, descHl: String, urlHl: String, pathHl: String, tagsHl: String): Pair<String, String>? {
        fun marked(s: String) = s.indexOf('\u0002') >= 0
        if (marked(nameHl) || marked(descHl)) return null
        return when {
            marked(urlHl) -> "link" to snippet(urlHl.replace(Regex("^https?://(www\\.)?"), ""))
            marked(pathHl) -> "category" to pathHl.replace(Regex("\\s*>\\s*"), " \u203A ")
            marked(tagsHl) -> "tags" to tagsHl.replace("|", ", ")
            else -> null
        }
    }

    /** Trims [hl] to about [max] characters around its first match, keeping the markers intact. */
    private fun snippet(hl: String, max: Int = 64): String {
        if (hl.length <= max) return hl
        val first = hl.indexOf('\u0002').coerceAtLeast(0)
        var start = (first - max / 4).coerceAtLeast(0)
        val end = (start + max).coerceAtMost(hl.length)
        start = (end - max).coerceAtLeast(0)
        var out = hl.substring(start, end)
        if (out.count { it == '\u0002' } > out.count { it == '\u0003' }) out += "\u0003"
        if (out.count { it == '\u0003' } > out.count { it == '\u0002' }) out = "\u0002$out"
        return (if (start > 0) "\u2026" else "") + out + (if (end < hl.length) "\u2026" else "")
    }

    /** Wraps every case-insensitive occurrence of [words] in [text] with the highlight markers. */
    internal fun markWords(text: String, words: List<String>): String {
        val marked = BooleanArray(text.length)
        for (w in words) {
            if (w.isEmpty()) continue
            var i = text.indexOf(w, 0, ignoreCase = true)
            while (i >= 0) {
                for (k in i until minOf(text.length, i + w.length)) marked[k] = true
                i = text.indexOf(w, i + w.length, ignoreCase = true)
            }
        }
        return buildString {
            var open = false
            for ((idx, ch) in text.withIndex()) {
                if (marked[idx] && !open) { append('\u0002'); open = true }
                if (!marked[idx] && open) { append('\u0003'); open = false }
                append(ch)
            }
            if (open) append('\u0003')
        }
    }

    private suspend fun searchTrigram(
        target: String,
        otherWords: List<String>,
        query: Query,
        effectiveSections: Set<String>,
        limit: Int,
    ): List<SearchHit> {
        val matchExpr = "\"${target.replace("\"", "\"\"")}\""
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions.add("fts_tri MATCH ?")
        params.add(matchExpr)

        if (query.isStarred) conditions.add("e.tier >= 1")
        if (query.isTop) conditions.add("e.tier = 2")
        if (query.isB64) conditions.add("e.is_base64 = 1")
        if (query.site != null) {
            conditions.add("(e.url LIKE '%://' || ? || '/%' OR e.url LIKE '%://' || ? OR e.url LIKE '%.' || ? || '/%' OR e.url LIKE '%.' || ?)")
            params.add(query.site)
            params.add(query.site)
            params.add(query.site)
            params.add(query.site)
        }
        if (effectiveSections.isNotEmpty()) {
            val placeholders = effectiveSections.joinToString(",") { "?" }
            conditions.add("e.section_id IN ($placeholders)")
            effectiveSections.forEach { params.add(it) }
        }
        // The other words must appear somewhere in the entry too (as substrings).
        for (w in otherWords) {
            conditions.add("(e.name || ' ' || e.description || ' ' || e.tags || ' ' || e.heading_path || ' ' || e.url) LIKE '%' || ? || '%'")
            params.add(w)
        }
        // Same excludes the FTS path applies with NOT, matched as case-insensitive substrings.
        for (ex in query.excludes) {
            conditions.add("(e.name NOT LIKE '%' || ? || '%' AND e.description NOT LIKE '%' || ? || '%' AND e.url NOT LIKE '%' || ? || '%')")
            params.add(ex)
            params.add(ex)
            params.add(ex)
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        val sql = """
            SELECT
                e.num, e.id, e.section_id, e.heading_id, e.ord, e.name, e.url, e.original_url,
                e.description, e.tags, e.heading_path, e.tier, e.kind, e.line, e.is_inline_more, e.is_base64,
                e.mirrors, e.extra_links, e.first_seen, e.last_changed, e.starred_at
            FROM fts_tri
            JOIN entries e ON fts_tri.rowid = e.num
            $whereClause
            ORDER BY e.tier DESC, e.name ASC
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<SearchHit>()
        store.withConnection { conn ->
            conn.prepare(sql).use { stmt ->
                var idx = 1
                for (p in params) {
                    when (p) {
                        is String -> stmt.bindText(idx++, p)
                        is Long -> stmt.bindLong(idx++, p)
                        is Int -> stmt.bindLong(idx++, p.toLong())
                    }
                }
                stmt.bindLong(idx, limit.toLong())

                while (stmt.step()) {
                    val entry = readEntry(stmt)
                    val words = listOf(target) + otherWords
                    results.add(SearchHit(entry, markWords(entry.name, words), markWords(entry.description, words)))
                }
            }
        }
        return results
    }

    private suspend fun searchPlain(
        query: Query,
        effectiveSections: Set<String>,
        limit: Int,
    ): List<SearchHit> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (query.isStarred) conditions.add("e.tier >= 1")
        if (query.isTop) conditions.add("e.tier = 2")
        if (query.isB64) conditions.add("e.is_base64 = 1")
        if (query.site != null) {
            conditions.add("(e.url LIKE '%://' || ? || '/%' OR e.url LIKE '%://' || ? OR e.url LIKE '%.' || ? || '/%' OR e.url LIKE '%.' || ?)")
            params.add(query.site)
            params.add(query.site)
            params.add(query.site)
            params.add(query.site)
        }
        if (effectiveSections.isNotEmpty()) {
            val placeholders = effectiveSections.joinToString(",") { "?" }
            conditions.add("e.section_id IN ($placeholders)")
            effectiveSections.forEach { params.add(it) }
        }
        if (query.excludes.isNotEmpty()) {
            for (ex in query.excludes) {
                conditions.add("(e.name NOT LIKE '%' || ? || '%' AND e.description NOT LIKE '%' || ? || '%')")
                params.add(ex)
                params.add(ex)
            }
        }

        val whereClause = if (conditions.isNotEmpty()) "WHERE " + conditions.joinToString(" AND ") else ""
        val sql = """
            SELECT
                e.num, e.id, e.section_id, e.heading_id, e.ord, e.name, e.url, e.original_url,
                e.description, e.tags, e.heading_path, e.tier, e.kind, e.line, e.is_inline_more, e.is_base64,
                e.mirrors, e.extra_links, e.first_seen, e.last_changed, e.starred_at
            FROM entries e
            $whereClause
            ORDER BY e.tier DESC, e.name ASC
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<SearchHit>()
        store.withConnection { conn ->
            conn.prepare(sql).use { stmt ->
                var idx = 1
                for (p in params) {
                    when (p) {
                        is String -> stmt.bindText(idx++, p)
                        is Long -> stmt.bindLong(idx++, p)
                        is Int -> stmt.bindLong(idx++, p.toLong())
                    }
                }
                stmt.bindLong(idx, limit.toLong())

                while (stmt.step()) {
                    val entry = readEntry(stmt)
                    results.add(SearchHit(entry, entry.name, entry.description))
                }
            }
        }
        return results
    }

    private fun readEntry(stmt: androidx.sqlite.SQLiteStatement): Entry {
        val tagsRaw = stmt.getText(9)
        val tagsList = if (tagsRaw.isEmpty()) emptyList() else tagsRaw.split('|')
        val mirrorsRaw = stmt.getText(16)
        val mirrorsList = try {
            json.decodeFromString<List<Link>>(mirrorsRaw)
        } catch (_: Exception) {
            emptyList()
        }
        val extraLinksRaw = stmt.getText(17)
        val extraLinksList = try {
            json.decodeFromString<List<Link>>(extraLinksRaw)
        } catch (_: Exception) {
            emptyList()
        }

        return Entry(
            num = stmt.getLong(0).toInt(),
            id = stmt.getText(1),
            sectionId = stmt.getText(2),
            headingId = if (stmt.isNull(3)) null else stmt.getText(3),
            ord = stmt.getLong(4).toInt(),
            name = stmt.getText(5),
            url = stmt.getText(6),
            originalUrl = if (stmt.isNull(7)) null else stmt.getText(7),
            description = stmt.getText(8),
            tags = tagsList,
            headingPath = stmt.getText(10),
            tier = Tier.fromInt(stmt.getLong(11).toInt()),
            kind = EntryKind.fromInt(stmt.getLong(12).toInt()),
            line = stmt.getLong(13).toInt(),
            isInlineMore = stmt.getLong(14) != 0L,
            isBase64 = stmt.getLong(15) != 0L,
            mirrors = mirrorsList,
            extraLinks = extraLinksList,
            firstSeen = stmt.getLong(18),
            lastChanged = stmt.getLong(19),
            starredAt = if (stmt.isNull(20)) null else stmt.getLong(20),
        )
    }
}
