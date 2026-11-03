package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class VtMark(val flagged: Int?, val engines: Int?, val checkedAt: Long)

@Serializable
private data class VtMarkDto(
    val f: Int? = null,
    val e: Int? = null,
    val at: Long = 0L,
)

class VtSiteChecks(
    private val userStore: UserStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val KEY = "vt_checks"           // the single UserStore.kv key
        const val TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val CAP = 500
        fun normalizeHost(host: String): String = host.trim().lowercase()
        /** Only a clean result is ever remembered. */
        fun shouldRemember(verdict: ScanVerdict): Boolean = verdict == ScanVerdict.NO_FLAGS

        private val jsonCodec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }
    }

    private val mutex = Mutex()
    private var isLoaded = false
    private val _checked = MutableStateFlow<Map<String, VtMark>>(emptyMap())
    val checked: StateFlow<Map<String, VtMark>> = _checked.asStateFlow()

    suspend fun load() {
        mutex.withLock {
            if (isLoaded) return@withLock
            val raw = userStore.get(KEY)
            val parsed = parseJson(raw)
            val now = clock()
            val valid = parsed.filterValues { now - it.checkedAt < TTL_MS }
            _checked.value = valid
            isLoaded = true
        }
    }

    fun markFor(host: String): VtMark? {
        val norm = normalizeHost(host)
        val mark = _checked.value[norm] ?: return null
        if (clock() - mark.checkedAt >= TTL_MS) {
            return null
        }
        return mark
    }

    suspend fun remember(host: String, flagged: Int?, engines: Int?) {
        mutex.withLock {
            if (!isLoaded) {
                val raw = userStore.get(KEY)
                val parsed = parseJson(raw)
                val now = clock()
                _checked.value = parsed.filterValues { now - it.checkedAt < TTL_MS }
                isLoaded = true
            }
            val now = clock()
            val norm = normalizeHost(host)
            val updated = _checked.value.toMutableMap()
            updated[norm] = VtMark(flagged = flagged, engines = engines, checkedAt = now)
            val nonExpired = updated.filterValues { now - it.checkedAt < TTL_MS }
            val capped = if (nonExpired.size > CAP) {
                nonExpired.entries
                    .sortedByDescending { it.value.checkedAt }
                    .take(CAP)
                    .associate { it.key to it.value }
            } else {
                nonExpired
            }
            val json = encodeJson(capped)
            userStore.put(KEY, json)
            _checked.value = capped
        }
    }

    suspend fun forgetAll() {
        mutex.withLock {
            userStore.deleteKvPrefix(KEY)
            _checked.value = emptyMap()
            isLoaded = true
        }
    }

    private fun parseJson(raw: String?): Map<String, VtMark> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val dtos = jsonCodec.decodeFromString<Map<String, VtMarkDto>>(raw)
            dtos.mapValues { (_, dto) ->
                VtMark(flagged = dto.f, engines = dto.e, checkedAt = dto.at)
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun encodeJson(marks: Map<String, VtMark>): String {
        val dtos = marks.mapValues { (_, mark) ->
            VtMarkDto(f = mark.flagged, e = mark.engines, at = mark.checkedAt)
        }
        return jsonCodec.encodeToString(dtos)
    }
}
