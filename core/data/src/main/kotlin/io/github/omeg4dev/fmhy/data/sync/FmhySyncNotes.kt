package io.github.omeg4dev.fmhy.data.sync

import androidx.sqlite.SQLiteConnection
import io.github.omeg4dev.fmhy.data.linkedNoteIdsImpl
import io.github.omeg4dev.fmhy.data.storedCriticalNotesImpl
import io.github.omeg4dev.fmhy.data.parse.CriticalNoteRow
import io.github.omeg4dev.fmhy.data.parse.CriticalNotes
import io.github.omeg4dev.fmhy.data.parse.ParsedSection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

const val NOTE_RECHECK_SEC: Long = 86_400L

internal data class NotesSyncResult(
    val hasChanges: Boolean,
    val upserts: List<CriticalNoteRow>,
    val deletes: List<String>,
    val fileStates: Map<String, FileState>,
)

@Suppress("TooGenericExceptionCaught")
internal suspend fun FmhySync.syncNotesPhase(
    state: SyncState,
    parsedSections: List<ParsedSection>,
    now: Long,
    primarySuccessCount: AtomicInteger,
    fallbackSuccessCount: AtomicInteger,
): NotesSyncResult {
    return try {
        syncNotesPhaseInternal(state, parsedSections, now, primarySuccessCount, fallbackSuccessCount)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("FmhySync: critical notes sync failed: ${e.message}")
        val candidateIds = try {
            store.linkedNoteIdsImpl() + parsedSections.flatMap { it.noteLinks }.map { it.noteId }
        } catch (_: Exception) {
            emptySet()
        }
        val safeStates = state.files.filter { (key, _) ->
            candidateIds.any { id -> key == CriticalNotes.notePath(id) }
        }
        NotesSyncResult(
            hasChanges = false,
            upserts = emptyList(),
            deletes = emptyList(),
            fileStates = safeStates,
        )
    }
}

private suspend fun FmhySync.syncNotesPhaseInternal(
    state: SyncState,
    parsedSections: List<ParsedSection>,
    now: Long,
    primarySuccessCount: AtomicInteger,
    fallbackSuccessCount: AtomicInteger,
): NotesSyncResult {
    val linkedInStore = store.linkedNoteIdsImpl()
    val linkedInParsed = parsedSections.flatMap { it.noteLinks }.map { it.noteId }
    val candidateNoteIds = (linkedInStore + linkedInParsed).toSet()

    if (candidateNoteIds.isEmpty()) {
        val storedNotes = store.storedCriticalNotesImpl()
        return NotesSyncResult(
            hasChanges = storedNotes.isNotEmpty(),
            upserts = emptyList(),
            deletes = emptyList(),
            fileStates = emptyMap(),
        )
    }

    val storedNotes = store.storedCriticalNotesImpl()
    val unreferencedStored = storedNotes.keys.any { it !in candidateNoteIds }
    var hasChanges = unreferencedStored

    val notesFileStates = mutableMapOf<String, FileState>()
    val toFetch = mutableListOf<String>()

    for (id in candidateNoteIds) {
        val path = CriticalNotes.notePath(id)
        val noteState = state.files[path]
        val isCriticalByName = id.endsWith("-critical")
        val isStored = storedNotes.containsKey(id)

        val mustCheck = isCriticalByName || isStored || noteState == null || noteState.critical == null
        val shouldFetch = mustCheck || (noteState != null && (now - noteState.checkedAt) >= NOTE_RECHECK_SEC)

        if (shouldFetch) {
            toFetch.add(id)
        } else if (noteState != null) {
            notesFileStates[path] = noteState
        }
    }

    val upserts = mutableListOf<CriticalNoteRow>()
    val deletes = mutableListOf<String>()

    if (toFetch.isNotEmpty()) {
        val fetchResults = coroutineScope {
            toFetch.map { id ->
                async {
                    val path = CriticalNotes.notePath(id)
                    val noteState = state.files[path]
                    val isStored = storedNotes.containsKey(id)
                    val allowConditional = (noteState?.critical != true) || isStored
                    val etag = if (allowConditional) noteState?.etag else null
                    val primaryUrl = "${sources.primary.trimEnd('/')}/$path"
                    val fallbackUrl = "${sources.fallback.trimEnd('/')}/$path"

                    var res = fetchFile(
                        path = path,
                        urlPrimary = primaryUrl,
                        urlFallback = fallbackUrl,
                        etag = etag,
                        primaryCount = primarySuccessCount,
                        fallbackCount = fallbackSuccessCount,
                        keepRaw = false,
                    )

                    // A 304 for an id expected critical with no stored row triggers an unconditional refetch
                    if (res is FetchResult.NotModified && (noteState?.critical == true) && !isStored) {
                        res = fetchFile(
                            path = path,
                            urlPrimary = primaryUrl,
                            urlFallback = fallbackUrl,
                            etag = null,
                            primaryCount = primarySuccessCount,
                            fallbackCount = fallbackSuccessCount,
                            keepRaw = false,
                        )
                    }

                    Triple(id, path, res)
                }
            }.awaitAll()
        }

        for ((id, path, res) in fetchResults) {
            val noteState = state.files[path]
            val existing = storedNotes[id]

            when (res) {
                is FetchResult.Content -> {
                    val parsed = CriticalNotes.parseNote(id, res.body)
                    val isCritical = (parsed != null)
                    notesFileStates[path] = FileState(
                        etag = res.etag,
                        sha256 = res.sha256,
                        checkedAt = now,
                        critical = isCritical,
                    )
                    if (parsed != null) {
                        if (existing == null) {
                            upserts.add(parsed)
                            hasChanges = true
                        } else if (existing.title != parsed.title || existing.textMd != parsed.textMd || existing.hash != parsed.hash) {
                            upserts.add(parsed)
                            hasChanges = true
                        }
                    } else {
                        if (existing != null) {
                            deletes.add(id)
                            hasChanges = true
                        }
                    }
                }
                is FetchResult.NotModified -> {
                    val prev = noteState ?: FileState(
                        etag = res.etag,
                        sha256 = "",
                        checkedAt = now,
                        critical = existing != null,
                    )
                    notesFileStates[path] = prev.copy(
                        etag = res.etag ?: prev.etag,
                        checkedAt = now,
                    )
                }
                is FetchResult.NotFound -> {
                    notesFileStates[path] = FileState(
                        etag = null,
                        sha256 = "",
                        checkedAt = now,
                        critical = false,
                    )
                    if (existing != null) {
                        deletes.add(id)
                        hasChanges = true
                    }
                }
                is FetchResult.Error -> {
                    log("FmhySync: note $id error: ${res.message}")
                    if (noteState != null) {
                        notesFileStates[path] = noteState
                    }
                }
            }
        }
    }

    return NotesSyncResult(
        hasChanges = hasChanges,
        upserts = upserts,
        deletes = deletes,
        fileStates = notesFileStates,
    )
}

internal fun applyCriticalNotes(
    conn: SQLiteConnection,
    upserts: List<CriticalNoteRow>,
    deletes: List<String>,
) {
    if (deletes.isNotEmpty()) {
        conn.prepare("DELETE FROM critical_notes WHERE id = ?").use { stmt ->
            for (id in deletes) {
                stmt.bindText(1, id)
                stmt.step()
                stmt.reset()
            }
        }
    }
    if (upserts.isNotEmpty()) {
        val sqlUpsert = """
            INSERT INTO critical_notes (id, title, text_md, hash)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                text_md = excluded.text_md,
                hash = excluded.hash
        """.trimIndent()
        conn.prepare(sqlUpsert).use { stmt ->
            for (cn in upserts) {
                stmt.bindText(1, cn.id)
                stmt.bindText(2, cn.title)
                stmt.bindText(3, cn.textMd)
                stmt.bindText(4, cn.hash)
                stmt.step()
                stmt.reset()
            }
        }
    }
}
