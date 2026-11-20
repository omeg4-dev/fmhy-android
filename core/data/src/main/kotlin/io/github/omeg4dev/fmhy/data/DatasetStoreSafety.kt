package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.safety.HostMatch
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.SiteListRow
import io.github.omeg4dev.fmhy.data.safety.Via

// DatasetStore queries: safety checks, host matching, lookalike candidates, unsafe domains.

internal suspend fun DatasetStore.linkCountImpl(): Int = withConnection { conn ->
    val sql = "SELECT COUNT(*) + COALESCE(SUM(json_array_length(mirrors)), 0) + COALESCE(SUM(json_array_length(extra_links)), 0) FROM entries WHERE kind = 0"
    try {
        conn.prepare(sql).use { stmt ->
            if (stmt.step()) stmt.getLong(0).toInt() else 0
        }
    } catch (_: Exception) {
        conn.prepare("SELECT mirrors, extra_links FROM entries WHERE kind = 0").use { stmt ->
            var count = 0
            while (stmt.step()) {
                count++
                val m = stmt.getText(0)
                val e = stmt.getText(1)
                if (m.isNotEmpty() && m != "[]") {
                    count += try { json.decodeFromString<List<Link>>(m).size } catch (_: Exception) { 0 }
                }
                if (e.isNotEmpty() && e != "[]") {
                    count += try { json.decodeFromString<List<Link>>(e).size } catch (_: Exception) { 0 }
                }
            }
            count
        }
    }
}

internal suspend fun DatasetStore.siteListEntriesImpl(keys: Collection<String>): List<SiteListRow> = withConnection { conn ->
    if (keys.isEmpty()) return@withConnection emptyList()
    val result = mutableListOf<SiteListRow>()
    for (chunk in keys.chunked(500)) {
        val placeholders = chunk.joinToString(",") { "?" }
        val sql = "SELECT key, list, reason FROM site_lists WHERE key IN ($placeholders)"
        try {
            conn.prepare(sql).use { stmt ->
                chunk.forEachIndexed { i, k -> stmt.bindText(i + 1, k) }
                while (stmt.step()) {
                    result.add(
                        SiteListRow(
                            key = stmt.getText(0),
                            list = stmt.getText(1),
                            reason = stmt.getText(2),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Table might not exist in older snapshot databases
        }
    }
    result
}

internal suspend fun DatasetStore.entryMatchesForHostImpl(host: String): List<HostMatch> = withConnection { conn ->
    val cleanHost = host.lowercase().removePrefix("www.")
    val isShared = SafeGuard.isSharedResourceHost(cleanHost)
    val esc = escapeSqlLike(cleanHost)

    val whereParts = mutableListOf<String>()
    val params = mutableListOf<String>()

    // Exact host in URL
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://$esc/%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://$esc")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://$esc?%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://$esc#%")

    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://$esc/%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://$esc")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://$esc?%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://$esc#%")

    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://www.$esc/%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://www.$esc")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://www.$esc?%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("https://www.$esc#%")

    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://www.$esc/%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://www.$esc")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://www.$esc?%")
    whereParts.add("url LIKE ? ESCAPE '\\'")
    params.add("http://www.$esc#%")

    // Mirrors & extra links
    whereParts.add("mirrors LIKE ? ESCAPE '\\'")
    params.add("%://$esc%")
    whereParts.add("mirrors LIKE ? ESCAPE '\\'")
    params.add("%://www.$esc%")

    whereParts.add("extra_links LIKE ? ESCAPE '\\'")
    params.add("%://$esc%")
    whereParts.add("extra_links LIKE ? ESCAPE '\\'")
    params.add("%://www.$esc%")

    // Subdomains for non-shared hosts
    if (!isShared) {
        whereParts.add("url LIKE ? ESCAPE '\\'")
        params.add("%.$esc/%")
        whereParts.add("url LIKE ? ESCAPE '\\'")
        params.add("%.$esc")
        whereParts.add("url LIKE ? ESCAPE '\\'")
        params.add("%.$esc?%")
        whereParts.add("url LIKE ? ESCAPE '\\'")
        params.add("%.$esc#%")

        whereParts.add("mirrors LIKE ? ESCAPE '\\'")
        params.add("%.$esc%")

        whereParts.add("extra_links LIKE ? ESCAPE '\\'")
        params.add("%.$esc%")
    }

    val sql = """
        SELECT num, id, section_id, heading_id, ord, name, url, original_url,
               description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
               mirrors, extra_links, first_seen, last_changed, starred_at
        FROM entries
        WHERE ${whereParts.joinToString(" OR ")}
    """.trimIndent()

    val candidateEntries = mutableListOf<Entry>()
    conn.prepare(sql).use { stmt ->
        params.forEachIndexed { i, p ->
            stmt.bindText(i + 1, p)
        }
        while (stmt.step()) {
            candidateEntries.add(readEntry(stmt))
        }
    }

    fun matchesTargetHost(h: String?): Boolean {
        if (h == null) return false
        return h == cleanHost || (!isShared && h.endsWith(".$cleanHost"))
    }

    val matches = mutableListOf<HostMatch>()
    for (entry in candidateEntries) {
        val entryHost = extractHost(entry.url)
        if (matchesTargetHost(entryHost)) {
            matches.add(HostMatch(entry, entry.url, Via.URL))
        }
        for (mirror in entry.mirrors) {
            val mirrorHost = extractHost(mirror.url)
            if (matchesTargetHost(mirrorHost)) {
                matches.add(HostMatch(entry, mirror.url, Via.MIRROR))
            }
        }
        for (extra in entry.extraLinks) {
            val extraHost = extractHost(extra.url)
            if (matchesTargetHost(extraHost)) {
                matches.add(HostMatch(entry, extra.url, Via.EXTRA))
            }
        }
    }
    matches
}

internal suspend fun DatasetStore.entriesMatchingHostImpl(host: String): List<Entry> {
    return entryMatchesForHost(host).map { it.entry }.distinctBy { it.id }
}

internal suspend fun DatasetStore.findLookalikeCandidatesImpl(mainLabel: String): List<Entry> = withConnection { conn ->
    if (mainLabel.length < 4) return@withConnection emptyList()
    val esc = escapeSqlLike(mainLabel.lowercase())
    val sql = """
        SELECT num, id, section_id, heading_id, ord, name, url, original_url,
               description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
               mirrors, extra_links, first_seen, last_changed, starred_at
        FROM entries
        WHERE url LIKE ? ESCAPE '\' OR name LIKE ? ESCAPE '\'
        LIMIT 100
    """.trimIndent()
    val result = mutableListOf<Entry>()
    conn.prepare(sql).use { stmt ->
        stmt.bindText(1, "%$esc%")
        stmt.bindText(2, "%$esc%")
        while (stmt.step()) {
            result.add(readEntry(stmt))
        }
    }
    try {
        val ftsSql = """
            SELECT e.num, e.id, e.section_id, e.heading_id, e.ord, e.name, e.url, e.original_url,
                   e.description, e.tags, e.heading_path, e.tier, e.kind, e.line, e.is_inline_more, e.is_base64,
                   e.mirrors, e.extra_links, e.first_seen, e.last_changed, e.starred_at
            FROM fts_tri
            JOIN entries e ON fts_tri.rowid = e.num
            WHERE fts_tri MATCH ?
            LIMIT 50
        """.trimIndent()
        val quoted = "\"" + mainLabel.lowercase().replace("\"", "\"\"") + "\""
        conn.prepare(ftsSql).use { stmt ->
            stmt.bindText(1, quoted)
            while (stmt.step()) {
                val e = readEntry(stmt)
                if (result.none { it.id == e.id }) {
                    result.add(e)
                }
            }
        }
    } catch (_: Exception) {
        // fts_tri might not be available or query fails
    }
    result
}

internal suspend fun DatasetStore.unsafeReasonImpl(url: String): String? = withConnection { conn ->
    val host = extractHost(url) ?: return@withConnection null
    val candidates = generateHostCandidates(host)
    if (candidates.isEmpty()) return@withConnection null

    val placeholders = candidates.joinToString(",") { "?" }
    val sql = "SELECT domain, reason FROM unsafe_domains WHERE domain IN ($placeholders)"

    val matches = mutableListOf<Pair<String, String>>()
    conn.prepare(sql).use { stmt ->
        for (i in candidates.indices) {
            stmt.bindText(i + 1, candidates[i])
        }
        while (stmt.step()) {
            matches.add(stmt.getText(0) to stmt.getText(1))
        }
    }

    // Return reason for the longest matching domain (most specific), or first
    matches.maxByOrNull { it.first.length }?.second
}
