package io.github.omeg4dev.fmhy.data.search

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import kotlinx.serialization.json.Json

open class SearchEngine(private val store: DatasetStore) {

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun search(
        query: String,
        sectionFilter: Set<String> = emptySet(),
        limit: Int = 200,
    ): SearchResults = search(QueryParser.parse(query), sectionFilter, limit)

    open suspend fun search(
        query: Query,
        sectionFilter: Set<String> = emptySet(),
        limit: Int = 200,
    ): SearchResults {
        val startTime = System.currentTimeMillis()

        val allSections = store.sections()
        val sectionsById = allSections.associateBy { it.id }

        // Resolve in:<prefix> from query against section id and title
        val querySectionIds = if (query.inSections.isNotEmpty()) {
            val matched = mutableSetOf<String>()
            for (pattern in query.inSections) {
                for (sec in allSections) {
                    if (sec.id.equals(pattern, ignoreCase = true) ||
                        sec.id.startsWith(pattern, ignoreCase = true) ||
                        sec.title.startsWith(pattern, ignoreCase = true)
                    ) {
                        matched.add(sec.id)
                    }
                }
            }
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
            searchFts(query, effectiveSections, limit)
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

        val tookMs = System.currentTimeMillis() - startTime
        return SearchResults(groups, allHits.size, tookMs)
    }

    private suspend fun searchFts(
        query: Query,
        effectiveSections: Set<String>,
        limit: Int,
    ): List<SearchHit> {
        val wordTerms = query.words.map { "\"${it.replace("\"", "\"\"")}\"*" }
        val phraseTerms = query.phrases.map { "\"${it.replace("\"", "\"\"")}\"" }
        val positiveTerms = wordTerms + phraseTerms
        val excludeTerms = query.excludes.map { "\"${it.replace("\"", "\"\"")}\"*" }

        val matchExpr = if (excludeTerms.isNotEmpty()) {
            positiveTerms.joinToString(" ") + " NOT " + excludeTerms.joinToString(" NOT ")
        } else {
            positiveTerms.joinToString(" ")
        }

        val primaryTerm = query.words.firstOrNull() ?: query.phrases.firstOrNull() ?: ""

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
        val sql = """
            SELECT
                e.num, e.id, e.section_id, e.heading_id, e.ord, e.name, e.url, e.original_url,
                e.description, e.tags, e.heading_path, e.tier, e.kind, e.line, e.is_inline_more, e.is_base64,
                e.mirrors, e.extra_links, e.first_seen, e.last_changed, e.starred_at,
                highlight(fts_words, 0, char(2), char(3)) AS name_hl,
                highlight(fts_words, 2, char(2), char(3)) AS desc_hl,
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
                    val nameHl = stmt.getText(20)
                    val descHl = stmt.getText(21)
                    hits.add(SearchHit(entry, nameHl, descHl))
                }
            }
        }

        // Trigram fallback check
        if (hits.size < 5) {
            val allWords = query.words + query.phrases.flatMap { it.split("\\s+".toRegex()) }
            val longestWord = allWords.maxByOrNull { it.length } ?: ""
            if (longestWord.length >= 3) {
                val triHits = searchTrigram(longestWord, query, effectiveSections, limit - hits.size)
                val existingIds = hits.map { it.entry.id }.toSet()
                for (th in triHits) {
                    if (th.entry.id !in existingIds) {
                        hits.add(th)
                    }
                }
            }
        }

        return hits
    }

    private suspend fun searchTrigram(
        target: String,
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
                    results.add(SearchHit(entry, entry.name, entry.description))
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
