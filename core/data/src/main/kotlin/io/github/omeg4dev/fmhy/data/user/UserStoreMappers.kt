package io.github.omeg4dev.fmhy.data.user

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.user.UserStore.Companion.executeSqlScript
import io.github.omeg4dev.fmhy.data.user.UserStore.Companion.loadSchemaSql

internal fun readUserVersion(conn: SQLiteConnection): Int {
    return try {
        conn.prepare("PRAGMA user_version;").use { stmt ->
            if (stmt.step()) stmt.getLong(0).toInt() else 0
        }
    } catch (_: Exception) {
        0
    }
}

internal fun UserStore.migrateIfNeeded(conn: SQLiteConnection) {
    val currentVersion = readUserVersion(conn)
    if (currentVersion < targetVersion) {
        conn.execSQL("BEGIN IMMEDIATE;")
        try {
            var v = currentVersion
            while (v < targetVersion) {
                val migration = customMigrations[v]
                if (migration != null) {
                    migration(conn)
                } else {
                    executeDefaultMigration(conn, v, v + 1)
                }
                v++
                conn.execSQL("PRAGMA user_version = $v;")
            }
            conn.execSQL("COMMIT;")
        } catch (e: Throwable) {
            try {
                conn.execSQL("ROLLBACK;")
            } catch (_: Exception) {}
            throw e
        }
    }
}

internal fun columnExists(conn: SQLiteConnection, table: String, column: String): Boolean {
    return try {
        conn.prepare("PRAGMA table_info($table);").use { stmt ->
            while (stmt.step()) {
                if (stmt.getText(1).equals(column, ignoreCase = true)) {
                    return@use true
                }
            }
            false
        }
    } catch (_: Exception) {
        false
    }
}

internal fun executeDefaultMigration(conn: SQLiteConnection, fromVersion: Int, toVersion: Int) {
    if (fromVersion == 0 && toVersion >= 1) {
        executeSqlScript(conn, loadSchemaSql())
    }
    if (fromVersion == 1 && toVersion == 2) {
        if (!columnExists(conn, "follows", "excluded")) {
            conn.execSQL("ALTER TABLE follows ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0;")
        }
    }
    if (fromVersion == 2 && toVersion == 3) {
        conn.execSQL("""
            CREATE TABLE IF NOT EXISTS downloads (
              id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, file_name TEXT NOT NULL, mime_type TEXT NOT NULL DEFAULT '',
              source_host TEXT NOT NULL DEFAULT '', referer TEXT, user_agent TEXT, total_bytes INTEGER NOT NULL DEFAULT -1,
              downloaded_bytes INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, resumable INTEGER NOT NULL DEFAULT 1,
              restart_note INTEGER NOT NULL DEFAULT 0, allow_mobile INTEGER NOT NULL DEFAULT 0, error TEXT, file_uri TEXT,
              folder_label TEXT NOT NULL DEFAULT '', etag TEXT, last_modified TEXT, created_at INTEGER NOT NULL, finished_at INTEGER);
        """.trimIndent())
        conn.execSQL("CREATE INDEX IF NOT EXISTS downloads_created ON downloads(created_at DESC);")
    }
}

internal fun readBookmarksInternal(conn: SQLiteConnection): List<UserBookmark> {
    val result = mutableListOf<UserBookmark>()
    val sql = """
        SELECT entry_id, name, url, section_id, section_title, heading_path, saved_at, note, folder_id
        FROM bookmarks
        ORDER BY saved_at DESC
    """.trimIndent()
    conn.prepare(sql).use { stmt ->
        while (stmt.step()) {
            result.add(readBookmark(stmt))
        }
    }
    return result
}

internal fun readBookmarkInternal(conn: SQLiteConnection, entryId: String): UserBookmark? {
    val sql = """
        SELECT entry_id, name, url, section_id, section_title, heading_path, saved_at, note, folder_id
        FROM bookmarks
        WHERE entry_id = ?
    """.trimIndent()
    return conn.prepare(sql).use { stmt ->
        stmt.bindText(1, entryId)
        if (stmt.step()) readBookmark(stmt) else null
    }
}

internal fun readFoldersInternal(conn: SQLiteConnection): List<Folder> {
    val result = mutableListOf<Folder>()
    val sql = "SELECT id, name, ord, created_at FROM folders ORDER BY ord ASC, id ASC"
    conn.prepare(sql).use { stmt ->
        while (stmt.step()) {
            result.add(
                Folder(
                    id = stmt.getLong(0),
                    name = stmt.getText(1),
                    ord = stmt.getLong(2).toInt(),
                    createdAt = stmt.getLong(3),
                )
            )
        }
    }
    return result
}

internal fun readBookmark(stmt: SQLiteStatement): UserBookmark {
    return UserBookmark(
        entryId = stmt.getText(0),
        name = stmt.getText(1),
        url = stmt.getText(2),
        sectionId = stmt.getText(3),
        sectionTitle = stmt.getText(4),
        headingPath = stmt.getText(5),
        savedAt = stmt.getLong(6),
        note = stmt.getText(7),
        folderId = if (stmt.isNull(8)) null else stmt.getLong(8),
    )
}

internal fun bindBookmark(stmt: SQLiteStatement, b: UserBookmark) {
    stmt.bindText(1, b.entryId)
    stmt.bindText(2, b.name)
    stmt.bindText(3, b.url)
    stmt.bindText(4, b.sectionId)
    stmt.bindText(5, b.sectionTitle)
    stmt.bindText(6, b.headingPath)
    stmt.bindLong(7, b.savedAt)
    stmt.bindText(8, b.note)
    if (b.folderId != null) stmt.bindLong(9, b.folderId) else stmt.bindNull(9)
}

internal fun readSitePrefs(stmt: SQLiteStatement): SitePrefs {
    return SitePrefs(
        host = stmt.getText(0),
        adblock = if (stmt.isNull(1)) null else stmt.getLong(1) != 0L,
        popups = if (stmt.isNull(2)) null else stmt.getLong(2) != 0L,
        javascript = if (stmt.isNull(3)) null else stmt.getLong(3) != 0L,
        desktop = if (stmt.isNull(4)) null else stmt.getLong(4) != 0L,
        dark = if (stmt.isNull(5)) null else stmt.getLong(5) != 0L,
        updatedAt = stmt.getLong(6),
    )
}

internal fun bindBooleanOrNull(stmt: SQLiteStatement, index: Int, value: Boolean?) {
    if (value != null) {
        stmt.bindLong(index, if (value) 1L else 0L)
    } else {
        stmt.bindNull(index)
    }
}

internal fun countRows(conn: SQLiteConnection, table: String): Long {
    return conn.prepare("SELECT count(*) FROM $table").use { stmt ->
        if (stmt.step()) stmt.getLong(0) else 0L
    }
}
