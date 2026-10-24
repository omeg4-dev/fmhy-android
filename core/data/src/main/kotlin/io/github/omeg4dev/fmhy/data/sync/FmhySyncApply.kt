package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.parse.SectionInfo
import io.github.omeg4dev.fmhy.data.parse.SiteLists
import io.github.omeg4dev.fmhy.data.parse.UnsafeList

internal fun upsertSections(conn: SQLiteConnection, validSections: List<Pair<SectionInfo, FetchResult>>) {
    // C. Update sections table
    val sqlUpsertSection = """
        INSERT INTO sections (id, title, description, file, ord)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            title = excluded.title,
            description = excluded.description,
            file = excluded.file,
            ord = excluded.ord
    """.trimIndent()
    conn.prepare(sqlUpsertSection).use { stmt ->
        for ((sec, _) in validSections) {
            stmt.bindText(1, sec.id)
            stmt.bindText(2, sec.title)
            stmt.bindText(3, sec.description)
            stmt.bindText(4, sec.file)
            stmt.bindLong(5, sec.ord.toLong())
            stmt.step()
            stmt.reset()
        }
    }
}

internal fun replaceUnsafeDomains(
    conn: SQLiteConnection,
    unsafeChanged: Boolean,
    unsafeDomainsText: String,
    unsafeReasonsText: String,
) {
    // D. Replace unsafe_domains if those files changed
    if (unsafeChanged) {
        conn.execSQL("DELETE FROM unsafe_domains;")
        val unsafeList = UnsafeList.parse(unsafeDomainsText, unsafeReasonsText)
        conn.prepare("INSERT INTO unsafe_domains (domain, reason) VALUES (?, ?)").use { stmt ->
            for ((domain, reason) in unsafeList) {
                stmt.bindText(1, domain)
                stmt.bindText(2, reason)
                stmt.step()
                stmt.reset()
            }
        }
    }
}

internal fun replaceSiteLists(
    conn: SQLiteConnection,
    siteListsChanged: Boolean,
    siteListChanged: Boolean,
    siteListPlusChanged: Boolean,
    fmhySitesChanged: Boolean,
    unsafeReasonsText: String,
    siteListText: String,
    siteListPlusText: String,
    fmhySitesText: String,
) {
    // D2. Replace site_lists if those files changed
    if (siteListsChanged) {
        val reasonsMap = SiteLists.parseReasons(unsafeReasonsText)

        if (siteListChanged) {
            conn.execSQL("DELETE FROM site_lists WHERE list = 'unsafe';")
            val unsafeKeys = mutableSetOf<String>()
            conn.prepare("INSERT OR REPLACE INTO site_lists (key, list, reason) VALUES (?, 'unsafe', ?)").use { stmt ->
                for (k in SiteLists.parseList(siteListText)) {
                    unsafeKeys.add(k)
                    val host = k.substringBefore('/')
                    val r = reasonsMap[k] ?: reasonsMap[host] ?: reasonsMap["www.$host"] ?: ""
                    stmt.bindText(1, k)
                    stmt.bindText(2, r)
                    stmt.step()
                    stmt.reset()
                }
                for ((k, r) in reasonsMap) {
                    val norm = SiteLists.normalizeListKey(k) ?: continue
                    if (k.contains('/')) {
                        stmt.bindText(1, norm)
                        stmt.bindText(2, r)
                        stmt.step()
                        stmt.reset()
                    }
                }
            }
        }

        if (siteListPlusChanged) {
            conn.execSQL("DELETE FROM site_lists WHERE list = 'potentially_unsafe';")
            conn.prepare("INSERT OR REPLACE INTO site_lists (key, list, reason) VALUES (?, 'potentially_unsafe', ?)").use { stmt ->
                for (k in SiteLists.parseList(siteListPlusText)) {
                    val host = k.substringBefore('/')
                    val r = reasonsMap[k] ?: reasonsMap[host] ?: reasonsMap["www.$host"] ?: ""
                    stmt.bindText(1, k)
                    stmt.bindText(2, r)
                    stmt.step()
                    stmt.reset()
                }
            }
        }

        if (fmhySitesChanged) {
            conn.execSQL("DELETE FROM site_lists WHERE list = 'fmhy';")
            conn.prepare("INSERT OR REPLACE INTO site_lists (key, list, reason) VALUES (?, 'fmhy', '')").use { stmt ->
                for (k in SiteLists.parseList(fmhySitesText)) {
                    stmt.bindText(1, k)
                    stmt.step()
                    stmt.reset()
                }
            }
        }
    }
}

internal fun writeMeta(conn: SQLiteConnection, currentVer: Long, now: Long, sourceHost: String) {
    // E. Meta: version + 1 if anything changed, last_sync, source
    val newVer = currentVer + 1
    conn.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES ('version', ?)").use { stmt ->
        stmt.bindText(1, newVer.toString())
        stmt.step()
    }
    conn.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES ('last_sync', ?)").use { stmt ->
        stmt.bindText(1, now.toString())
        stmt.step()
    }
    conn.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES ('source', ?)").use { stmt ->
        stmt.bindText(1, sourceHost)
        stmt.step()
    }
}
