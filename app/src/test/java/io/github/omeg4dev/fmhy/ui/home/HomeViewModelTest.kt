package io.github.omeg4dev.fmhy.ui.home

import android.content.Context
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.search.SearchEngine
import io.github.omeg4dev.fmhy.data.sync.DatasetSync
import io.github.omeg4dev.fmhy.di.AppContainer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {

    private class FakeDatasetStore(
        private val sectionsList: List<Section> = emptyList(),
        private val topPick: Entry? = null,
        private val versionVal: Long = 10L,
    ) : DatasetStore(File("fake_nonexistent.db")) {
        override val isAvailable = true
        override val changes = MutableStateFlow(versionVal)
        override suspend fun version(): Long = versionVal
        override suspend fun sections(): List<Section> = sectionsList
        override suspend fun randomTopPick(): Entry? = topPick
        override suspend fun countAddedSince(ts: Long): Int = 5
        override suspend fun meta(): Map<String, String> = mapOf(
            "version" to versionVal.toString(),
            "created" to "1700000000"
        )
    }

    private class FakeContainer(
        override val datasetStore: DatasetStore,
        override val settingsRepository: SettingsRepository,
    ) : AppContainer {
        override val context: Context get() = error("Not needed")
        override val appScope: CoroutineScope get() = CoroutineScope(Dispatchers.Unconfined)
        override val okHttpClient: OkHttpClient get() = error("Not needed")
        override val datasetSync: DatasetSync get() = error("Not needed")
        override val searchEngine: SearchEngine get() = SearchEngine(datasetStore)
        private val _isInit = MutableStateFlow(true)
        override val isInitialized: StateFlow<Boolean> = _isInit.asStateFlow()
        private val _events = MutableSharedFlow<String>()
        override val snackbarEvents: SharedFlow<String> = _events.asSharedFlow()
        override fun showSnackbar(message: String) { _events.tryEmit(message) }
        override fun initialize() {}
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val _lastVisit = MutableStateFlow(1000L)
        private val _openExternal = MutableStateFlow(false)
        private val _recents = MutableStateFlow<List<String>>(emptyList())

        override val lastVisitTs: Flow<Long> = _lastVisit.asStateFlow()
        override suspend fun setLastVisitTs(ts: Long) { _lastVisit.value = ts }

        override val openInExternalBrowser: Flow<Boolean> = _openExternal.asStateFlow()
        override suspend fun setOpenInExternalBrowser(open: Boolean) { _openExternal.value = open }

        override val recentSearches: Flow<List<String>> = _recents.asStateFlow()
        override suspend fun addRecentSearch(query: String) { _recents.value = listOf(query) + _recents.value }
        override suspend fun removeRecentSearch(query: String) { _recents.value = _recents.value - query }
        override suspend fun clearRecentSearches() { _recents.value = emptyList() }
    }

    @Test
    fun testHomeViewModelLoadsSectionsAndData() = runTest {
        val sampleSections = listOf(
            Section(id = "video", title = "Video", description = "Streaming", ord = 1, entryCount = 42),
            Section(id = "audio", title = "Audio", description = "Music", ord = 2, entryCount = 10),
        )
        val samplePick = Entry(
            num = 1,
            id = "e1",
            sectionId = "video",
            headingId = null,
            ord = 1,
            name = "Top Movie",
            url = "https://movie.org",
            originalUrl = null,
            description = "Top movies",
            tags = emptyList(),
            headingPath = "Video",
            tier = Tier.TOP,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 1000,
            lastChanged = 1000,
            starredAt = 1000,
        )

        val store = FakeDatasetStore(sectionsList = sampleSections, topPick = samplePick, versionVal = 10L)
        val fakeSettings = FakeSettingsRepository()
        val fakeContainer = FakeContainer(store, fakeSettings)

        val viewModel = HomeViewModel(store, fakeSettings, fakeContainer)

        val readyState = viewModel.uiState.filter { it.isReady }.first()
        assertTrue(readyState.isReady)
        assertEquals(10L, readyState.datasetVersion)
        assertEquals(2, readyState.sections.size)
        assertEquals("video", readyState.sections[0].id)
        assertNotNull(readyState.randomPick)
        assertEquals("e1", readyState.randomPick?.id)
        assertEquals(5, readyState.newSinceLastVisitCount)
        assertFalse(readyState.isEmptyState)
    }
}
