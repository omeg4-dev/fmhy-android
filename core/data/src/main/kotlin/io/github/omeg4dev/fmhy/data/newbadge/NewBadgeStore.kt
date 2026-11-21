package io.github.omeg4dev.fmhy.data.newbadge

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BadgeMark(
    @SerialName("u") val u: String,
    @SerialName("d") val d: String,
)

@Serializable
data class PageState(
    val mdSha: String = "",
    val bodySha: String = "",
    val dirty: Int = 0,
    val fetchedAt: Long = 0L,
    val marks: List<BadgeMark> = emptyList(),
)

@Serializable
data class NewBadgeState(
    val version: Int = 1,
    val checkedAt: Long = 0L,
    val fetchedAt: Long = 0L,
    val pages: Map<String, PageState> = emptyMap(),
)

class NewBadgeStore(
    private val dir: File,
    private val log: (String) -> Unit = {},
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val stateFile = File(dir, "new-badges.json")
    private val _index = MutableStateFlow(NewBadgeIndex.EMPTY)
    val index: StateFlow<NewBadgeIndex> = _index.asStateFlow()

    init {
        load()
    }

    fun load() {
        val state = loadState()
        _index.value = state.toIndex()
    }

    @Suppress("TooGenericExceptionCaught")
    fun loadState(): NewBadgeState {
        if (!stateFile.exists()) return NewBadgeState()
        return try {
            val text = stateFile.readText()
            val parsed = json.decodeFromString<NewBadgeState>(text)
            if (parsed.version != 1) {
                log("NewBadgeStore: unsupported version ${parsed.version}")
                NewBadgeState()
            } else {
                parsed
            }
        } catch (e: Exception) {
            log("NewBadgeStore: failed to load state: ${e.message}")
            NewBadgeState()
        }
    }

    fun save(state: NewBadgeState) {
        save(state, validSections = null)
    }

    @Suppress("TooGenericExceptionCaught")
    fun save(state: NewBadgeState, validSections: Set<String>?) {
        try {
            val prunedState = prune(state, validSections)
            dir.mkdirs()
            val tmp = File(dir, "new-badges.json.tmp")
            tmp.writeText(json.encodeToString(prunedState))
            try {
                Files.move(
                    tmp.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(
                    tmp.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            _index.value = prunedState.toIndex()
        } catch (e: Exception) {
            log("NewBadgeStore: failed to save state: ${e.message}")
        }
    }

    private fun prune(state: NewBadgeState, validSections: Set<String>?): NewBadgeState {
        val today = clock()
        val cutoff = today.minusDays(30)
        val filteredPages = if (validSections != null) {
            state.pages.filterKeys { it in validSections }
        } else {
            state.pages
        }

        val prunedPages = filteredPages.mapValues { (_, pageState) ->
            val validMarks = pageState.marks
                .filter { mark ->
                    try {
                        val d = LocalDate.parse(mark.d)
                        !d.isBefore(cutoff)
                    } catch (_: Exception) {
                        false
                    }
                }
                .groupBy { it.u }
                .mapValues { (_, marks) -> marks.maxBy { it.d } }
                .values
                .toList()
            pageState.copy(marks = validMarks)
        }

        return state.copy(pages = prunedPages)
    }

    companion object {
        fun NewBadgeState.toIndex(): NewBadgeIndex {
            if (version != 1 || pages.isEmpty()) return NewBadgeIndex.EMPTY
            val map = mutableMapOf<String, LocalDate>()
            for ((sectionId, page) in pages) {
                for (mark in page.marks) {
                    val date = try {
                        LocalDate.parse(mark.d)
                    } catch (_: Exception) {
                        continue
                    }
                    val key = NewBadgeKeys.pageKey(sectionId, mark.u)
                    val existing = map[key]
                    if (existing == null || date.isAfter(existing)) {
                        map[key] = date
                    }
                }
            }
            return NewBadgeIndex(map)
        }
    }
}
