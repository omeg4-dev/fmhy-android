package io.github.omeg4dev.fmhy.data

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.data.model.Note
import io.github.omeg4dev.fmhy.data.model.Tier
import java.net.URI

internal fun escapeSqlLike(input: String): String {
    return input.replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}

internal fun readHeading(stmt: androidx.sqlite.SQLiteStatement): Heading {
    return Heading(
        id = stmt.getText(0),
        sectionId = stmt.getText(1),
        parentId = if (stmt.isNull(2)) null else stmt.getText(2),
        level = stmt.getLong(3).toInt(),
        title = stmt.getText(4),
        anchor = stmt.getText(5),
        ord = stmt.getLong(6).toInt(),
    )
}

internal fun DatasetStore.readEntry(stmt: androidx.sqlite.SQLiteStatement): Entry {
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

internal fun extractHost(url: String): String? {
    val clean = url.trim()
    val rawHost = try {
        val u = if (!clean.contains("://")) "http://$clean" else clean
        URI(u).host
    } catch (_: Exception) {
        null
    } ?: clean.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')

    return rawHost.lowercase().removePrefix("www.")
}

internal fun generateHostCandidates(host: String): List<String> {
    val parts = host.split('.')
    if (parts.size < 2) return listOf(host)
    val candidates = mutableListOf<String>()
    for (i in 0 until parts.size - 1) {
        candidates.add(parts.subList(i, parts.size).joinToString("."))
    }
    return candidates
}

internal sealed interface RawItem {
    val ord: Int
    val headingId: String?
    data class H(val heading: Heading) : RawItem {
        override val ord get() = heading.ord
        override val headingId get() = heading.id
    }
    data class N(val note: Note) : RawItem {
        override val ord get() = note.ord
        override val headingId get() = note.headingId
    }
    data class E(val entry: Entry) : RawItem {
        override val ord get() = entry.ord
        override val headingId get() = entry.headingId
    }
}
