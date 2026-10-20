package io.github.omeg4dev.fmhy.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val keyBookmarksV1 = stringPreferencesKey("bookmarks_v1")

    @Test
    fun testMigrationThreeLegacyBookmarks() = runTest {
        val prefsFile = tmp.newFile("legacy_bookmarks.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { prefsFile },
        )

        val legacyList = listOf(
            Bookmark(
                entryId = "e1",
                name = "Site 1",
                url = "https://example.com/1",
                sectionId = "video",
                sectionTitle = "Video",
                headingPath = "Video > Movies",
                savedAt = 1000L,
            ),
            Bookmark(
                entryId = "e2",
                name = "Site 2",
                url = "https://example.com/2",
                sectionId = "audio",
                sectionTitle = "Audio",
                headingPath = "Audio > Music",
                savedAt = 2000L,
            ),
            Bookmark(
                entryId = "e3",
                name = "Site 3",
                url = "https://example.com/3",
                sectionId = "tools",
                sectionTitle = "Tools",
                headingPath = "Tools > Dev",
                savedAt = 3000L,
            ),
        )

        // Seed legacy DataStore with JSON
        dataStore.edit { prefs ->
            prefs[keyBookmarksV1] = Json.encodeToString(legacyList)
        }

        val dbFile = tmp.newFile("migrated_user.db")
        val userStore = UserStore(dbFile)
        val repository = BookmarksRepository(
            userStore = userStore,
            legacyDataStore = dataStore,
        )

        // Run migration
        repository.migrateLegacyIfNeeded()

        // 1. bookmarks.first() has 3 with identical fields
        val migrated = repository.bookmarks.first()
        assertEquals(3, migrated.size)

        // Newest first order
        assertEquals("e3", migrated[0].entryId)
        assertEquals("Site 3", migrated[0].name)
        assertEquals("https://example.com/3", migrated[0].url)
        assertEquals("tools", migrated[0].sectionId)
        assertEquals("Tools", migrated[0].sectionTitle)
        assertEquals("Tools > Dev", migrated[0].headingPath)
        assertEquals(3000L, migrated[0].savedAt)
        assertEquals("", migrated[0].note)
        assertEquals(null, migrated[0].folderId)

        assertEquals("e2", migrated[1].entryId)
        assertEquals("Site 2", migrated[1].name)
        assertEquals(2000L, migrated[1].savedAt)

        assertEquals("e1", migrated[2].entryId)
        assertEquals("Site 1", migrated[2].name)
        assertEquals(1000L, migrated[2].savedAt)

        // 2. Flag set
        assertEquals("1", userStore.get("bookmarks_migrated_v1"))

        // 3. Second run produces no duplicates
        repository.migrateLegacyIfNeeded()
        val afterSecondRun = repository.bookmarks.first()
        assertEquals(3, afterSecondRun.size)

        // 4. Legacy key still present afterwards
        val currentPrefs = dataStore.data.first()
        val rawLegacy = currentPrefs[keyBookmarksV1]
        assertNotNull("Legacy key must not be deleted or modified", rawLegacy)
        assertEquals(Json.encodeToString(legacyList), rawLegacy)

        userStore.close()
    }

    @Test
    fun testCorruptLegacyJsonNoCrashAndFlagSetWithZero() = runTest {
        val prefsFile = tmp.newFile("corrupt_bookmarks.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { prefsFile },
        )

        // Seed legacy DataStore with corrupt JSON string
        val corruptJson = "{ invalid: json... not a list }"
        dataStore.edit { prefs ->
            prefs[keyBookmarksV1] = corruptJson
        }

        val dbFile = tmp.newFile("corrupt_user.db")
        val userStore = UserStore(dbFile)
        val repository = BookmarksRepository(
            userStore = userStore,
            legacyDataStore = dataStore,
        )

        // Run migration: must not crash
        repository.migrateLegacyIfNeeded()

        // Empty is a valid result, flag set
        assertEquals("1", userStore.get("bookmarks_migrated_v1"))
        assertTrue(repository.bookmarks.first().isEmpty())

        // Legacy key still present afterwards with its original corrupt content
        val currentPrefs = dataStore.data.first()
        assertEquals(corruptJson, currentPrefs[keyBookmarksV1])

        userStore.close()
    }
}
