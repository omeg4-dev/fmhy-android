package io.github.omeg4dev.fmhy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fmhy_settings")

interface SettingsRepository {
    val lastVisitTs: Flow<Long>
    suspend fun setLastVisitTs(ts: Long)

    val openInExternalBrowser: Flow<Boolean>
    suspend fun setOpenInExternalBrowser(open: Boolean)

    val recentSearches: Flow<List<String>>
    suspend fun addRecentSearch(query: String)
    suspend fun removeRecentSearch(query: String)
    suspend fun clearRecentSearches()

    val appearance: Flow<Appearance>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setColorSource(source: ColorSource)
    suspend fun setPureBlack(enabled: Boolean)
    suspend fun setAccentSeed(argb: Int)
    suspend fun setSectionLayout(layout: SectionLayout)
}

class DefaultSettingsRepository(private val context: Context) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_LAST_VISIT_TS = longPreferencesKey("last_visit_ts")
        private val KEY_OPEN_EXTERNAL_BROWSER = booleanPreferencesKey("open_in_external_browser")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
        private val KEY_SECTION_LAYOUT = stringPreferencesKey("section_layout")
        private val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        private val KEY_ACCENT_SEED = intPreferencesKey("accent_seed")
    }

    override val lastVisitTs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_VISIT_TS] ?: 0L
    }

    override suspend fun setLastVisitTs(ts: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_VISIT_TS] = ts
        }
    }

    override val openInExternalBrowser: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPEN_EXTERNAL_BROWSER] ?: false
    }

    override suspend fun setOpenInExternalBrowser(open: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OPEN_EXTERNAL_BROWSER] = open
        }
    }

    override val recentSearches: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_RECENT_SEARCHES] ?: return@map emptyList()
        try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[KEY_RECENT_SEARCHES] ?: "[]"
                json.decodeFromString<List<String>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            existing.remove(trimmed)
            existing.add(0, trimmed)
            val updated = existing.take(10)
            prefs[KEY_RECENT_SEARCHES] = json.encodeToString(updated)
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        context.dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[KEY_RECENT_SEARCHES] ?: "[]"
                json.decodeFromString<List<String>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            existing.remove(query)
            prefs[KEY_RECENT_SEARCHES] = json.encodeToString(existing)
        }
    }

    override suspend fun clearRecentSearches() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_RECENT_SEARCHES)
        }
    }

    override val appearance: Flow<Appearance> = context.dataStore.data.map { prefs ->
        val themeMode = try {
            prefs[KEY_THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
        val colorSource = try {
            prefs[KEY_COLOR_SOURCE]?.let { ColorSource.valueOf(it) } ?: ColorSource.FMHY
        } catch (_: IllegalArgumentException) {
            ColorSource.FMHY
        }
        val sectionLayout = try {
            prefs[KEY_SECTION_LAYOUT]?.let { SectionLayout.valueOf(it) } ?: SectionLayout.CONTINUOUS
        } catch (_: IllegalArgumentException) {
            SectionLayout.CONTINUOUS
        }
        val pureBlack = prefs[KEY_PURE_BLACK] ?: false
        val accentSeed = prefs[KEY_ACCENT_SEED] ?: 0xFF7C5CFF.toInt()

        Appearance(
            themeMode = themeMode,
            colorSource = colorSource,
            pureBlack = pureBlack,
            accentSeed = accentSeed,
            sectionLayout = sectionLayout,
        )
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun setColorSource(source: ColorSource) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COLOR_SOURCE] = source.name
        }
    }

    override suspend fun setPureBlack(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PURE_BLACK] = enabled
        }
    }

    override suspend fun setAccentSeed(argb: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCENT_SEED] = argb
        }
    }

    override suspend fun setSectionLayout(layout: SectionLayout) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SECTION_LAYOUT] = layout.name
        }
    }
}
