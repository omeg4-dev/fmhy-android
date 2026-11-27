package io.github.omeg4dev.fmhy.data.fmhyx

import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.data.user.BookmarksSnapshot
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DATASET_SCHEMA
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.data.user.UserStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FmhyXSyncEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var primary: MockWebServer
    private lateinit var mirror: MockWebServer
    private lateinit var server: FakeFmhyXServer
    private lateinit var userStore: UserStore
    private lateinit var datasetStore: DatasetStore
    private lateinit var client: FmhyXClient

    private var nsfwList: List<String> = emptyList()
    private var nowMs = 1_700_000_000_000L
    private val idCounter = AtomicInteger(0)

    private val token = "fmhyx_test_token_123"

    @Before
    fun setUp() {
        server = FakeFmhyXServer(validToken = token)
        primary = MockWebServer().apply { dispatcher = server; start() }
        mirror = MockWebServer().apply { dispatcher = server; start() }

        userStore = UserStore(tempFolder.newFile("user.db"), clock = { nowMs })
        val dsFile = tempFolder.newFile("dataset.db")
        val conn = BundledSQLiteDriver().open(dsFile.absolutePath)
        DatasetStore.executeSqlScript(conn, DatasetStore.loadSchemaSql())
        conn.execSQL("INSERT INTO meta (key, value) VALUES ('schema', '$DATASET_SCHEMA'), ('version', '1');")
        conn.close()
        datasetStore = DatasetStore(dsFile)

        client = FmhyXClient(
            http = OkHttpClient(),
            baseUrls = listOf(primary.url("/").toString(), mirror.url("/").toString()),
        )
    }

    @After
    fun tearDown() {
        userStore.close()
        datasetStore.closeSync()
        primary.close()
        mirror.close()
    }

    private fun newEngine(
        resolver: SyncEntryResolver = DatasetSyncEntryResolver(datasetStore) { nsfwList },
        clock: () -> Long = { nowMs },
        newId: () -> String = { "00000000-0000-0000-0000-${idCounter.incrementAndGet().toString().padStart(12, '0')}" },
    ): FmhyXSyncEngine = FmhyXSyncEngine(
        store = userStore,
        client = client,
        resolver = resolver,
        clock = clock,
        newId = newId,
    )

    private suspend fun seedDataset(
        sections: List<Pair<String, String>> = listOf("video" to "Movies / TV", "privacy" to "Privacy"),
        entries: List<TestEntry> = emptyList(),
    ) {
        datasetStore.withConnection { conn ->
            sections.forEachIndexed { idx, (id, title) ->
                conn.prepare("INSERT OR REPLACE INTO sections (id, title, description, file, ord) VALUES (?, ?, '', ?, ?)")
                    .use { st ->
                        st.bindText(1, id)
                        st.bindText(2, title)
                        st.bindText(3, "$id.md")
                        st.bindLong(4, idx.toLong())
                        st.step()
                    }
            }
            entries.forEachIndexed { idx, e ->
                if (e.headingId != null) {
                    val anchor = e.headingId.substringAfter('/')
                    conn.prepare("INSERT OR IGNORE INTO headings (id, section_id, parent_id, level, title, anchor, ord) VALUES (?, ?, NULL, 1, ?, ?, ?)")
                        .use { st ->
                            st.bindText(1, e.headingId)
                            st.bindText(2, e.sectionId)
                            st.bindText(3, anchor)
                            st.bindText(4, anchor)
                            st.bindLong(5, idx.toLong())
                            st.step()
                        }
                }
                conn.prepare(
                    "INSERT INTO entries (num, id, section_id, heading_id, ord, name, url, original_url, description, tags, heading_path, tier, kind, line, is_inline_more, is_base64, mirrors, extra_links, first_seen, last_changed, starred_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?, 0, 0, 1, 0, ?, '[]', '[]', 1000, 1000, NULL)"
                ).use { st ->
                    st.bindLong(1, (idx + 1).toLong())
                    st.bindText(2, e.id)
                    st.bindText(3, e.sectionId)
                    if (e.headingId != null) st.bindText(4, e.headingId) else st.bindNull(4)
                    st.bindLong(5, idx.toLong())
                    st.bindText(6, e.name)
                    st.bindText(7, e.url)
                    if (e.originalUrl != null) st.bindText(8, e.originalUrl) else st.bindNull(8)
                    st.bindText(9, e.description)
                    st.bindText(10, e.headingPath)
                    st.bindLong(11, if (e.originalUrl != null) 1L else 0L)
                    st.step()
                }
            }
        }
    }

    private data class TestEntry(
        val id: String,
        val sectionId: String,
        val headingId: String?,
        val name: String,
        val url: String,
        val originalUrl: String? = null,
        val description: String = "",
        val headingPath: String = "",
    )

    private fun remoteItem(
        u: String,
        t: String = "Title",
        at: Long = 1000L,
        p: String? = null,
        h: String? = null,
        d: String? = null,
        extra: Pair<String, String>? = null,
    ): JsonObject = buildJsonObject {
        put("u", JsonPrimitive(u))
        put("t", JsonPrimitive(t))
        put("at", JsonPrimitive(at))
        if (p != null) put("p", JsonPrimitive(p))
        if (h != null) put("h", JsonPrimitive(h))
        if (d != null) put("d", JsonPrimitive(d))
        if (extra != null) put(extra.first, JsonPrimitive(extra.second))
    }

    private fun remoteCollection(
        id: String,
        name: String,
        items: List<JsonObject>,
        updated: Long = 1000L,
        share: String? = null,
        extra: Pair<String, String>? = null,
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("name", JsonPrimitive(name))
        put("items", JsonArray(items))
        put("updated", JsonPrimitive(updated))
        if (share != null) put("share", JsonPrimitive(share))
        if (extra != null) put(extra.first, JsonPrimitive(extra.second))
    }

    @Test
    fun mappingBothDirectionsDescriptionCutHeadingAnchorAndFallbacks() = runTest {
        val longDesc = "d".repeat(239) + "\uD83D\uDE00more"
        seedDataset(
            entries = listOf(
                TestEntry(
                    id = "eid_local",
                    sectionId = "privacy",
                    headingId = "privacy/adblocking",
                    name = "uBlock Origin",
                    url = "https://ublockorigin.com/",
                    description = longDesc,
                    headingPath = "Privacy > Adblocking",
                ),
                TestEntry(
                    id = "eid_sec1",
                    sectionId = "video",
                    headingId = "video/anime",
                    name = "Shared Site (Video)",
                    url = "https://shared.example/",
                    headingPath = "Movies / TV > Anime",
                ),
                TestEntry(
                    id = "eid_sec2_h1",
                    sectionId = "privacy",
                    headingId = "privacy/dns",
                    name = "Shared Site (DNS)",
                    url = "https://shared.example/",
                    headingPath = "Privacy > DNS",
                ),
                TestEntry(
                    id = "eid_sec2_h2",
                    sectionId = "privacy",
                    headingId = "privacy/vpn",
                    name = "Shared Site (VPN)",
                    url = "https://shared.example/",
                    headingPath = "Privacy > VPN",
                ),
                TestEntry(
                    id = "eid_b64",
                    sectionId = "video",
                    headingId = "video/streaming",
                    name = "Decoded Site",
                    url = "https://decoded.example/",
                    originalUrl = "https://rentry.co/FMHYB64#abc",
                    headingPath = "Movies / TV > Streaming",
                ),
            )
        )

        // Local bookmark with note (must NEVER be sent)
        userStore.upsertBookmark(
            UserBookmark(
                entryId = "eid_local",
                name = "uBlock Origin",
                url = "https://ublockorigin.com/",
                sectionId = "privacy",
                sectionTitle = "Privacy",
                headingPath = "Privacy > Adblocking",
                savedAt = 1500L,
                note = "Secret local note",
            )
        )
        // Colliding entryId already used locally with a different URL
        userStore.upsertBookmark(
            UserBookmark(
                entryId = "eid_b64",
                name = "Different Url",
                url = "https://different.example/",
                sectionId = "video",
                sectionTitle = "Movies / TV",
                headingPath = "",
                savedAt = 1200L,
            )
        )

        // Remote items: one matches p+h tie-break, one matches unknown -> fmhyx:<sha1_16>, one matches originalUrl but collides on eid_b64
        server.seedFavorite(remoteItem("https://shared.example/", t = "Wrong", at = 2000L, p = "privacy", h = "vpn"))
        server.seedFavorite(remoteItem("https://unknown.example/path", t = "Unknown Site", at = 0L, p = "misc"))
        server.seedFavorite(remoteItem("https://rentry.co/FMHYB64#abc", t = "B64 Item", at = 2500L, p = "video"))

        val engine = newEngine()
        val result = engine.sync(token)
        assertFalse(result.changesPending)
        assertEquals(nowMs, result.syncedAt)

        // Verify outgoing favorite for eid_local
        val pushedFav = server.favorites["https://ublockorigin.com/"]!!
        assertEquals("uBlock Origin", pushedFav.str("t"))
        assertEquals("privacy", pushedFav.str("p"))
        assertEquals("adblocking", pushedFav.str("h"))
        assertEquals("d".repeat(239), pushedFav.str("d"))
        assertEquals(1500L, pushedFav.longOrZero("at"))
        assertFalse("note" in pushedFav)

        // Verify pulled entries locally
        val localBookmarks = userStore.bookmarks().associateBy { it.url }
        val pulledShared = localBookmarks["https://shared.example/"]!!
        assertEquals("eid_sec2_h2", pulledShared.entryId)
        assertEquals("Shared Site (VPN)", pulledShared.name)
        assertEquals("Privacy", pulledShared.sectionTitle)
        assertEquals("Privacy > VPN", pulledShared.headingPath)
        assertEquals(2000L, pulledShared.savedAt)

        val pulledUnknown = localBookmarks["https://unknown.example/path"]!!
        assertEquals("fmhyx:${sha1Hex16("https://unknown.example/path")}", pulledUnknown.entryId)
        assertEquals("Unknown Site", pulledUnknown.name)
        assertEquals("misc", pulledUnknown.sectionId)
        assertEquals("", pulledUnknown.sectionTitle)
        assertEquals(nowMs, pulledUnknown.savedAt) // 0 -> now
        assertEquals("", pulledUnknown.note)

        val pulledColliding = localBookmarks["https://rentry.co/FMHYB64#abc"]!!
        assertEquals("fmhyx:${sha1Hex16("https://rentry.co/FMHYB64#abc")}", pulledColliding.entryId)
        assertEquals("B64 Item", pulledColliding.name)
    }

    @Test
    fun firstSignInMergeBothSidesAndSameSiteNewestAtWins() = runTest {
        seedDataset(
            entries = listOf(
                TestEntry("wiki_1", "video", null, "Wiki Name", "https://both-wiki.example/"),
            )
        )
        userStore.upsertBookmark(
            UserBookmark("only_local", "Local Only", "https://local.example/", "video", "Video", "", 1000L)
        )
        userStore.upsertBookmark(
            UserBookmark("wiki_1", "Wiki Name", "https://both-wiki.example/", "video", "Video", "", 1000L)
        )
        userStore.upsertBookmark(
            UserBookmark("fmhyx:abc", "Old Fmhyx Name", "https://both-fmhyx.example/", "", "", "", 1000L)
        )
        userStore.upsertBookmark(
            UserBookmark("local_newer", "Local Newer", "https://local-newer.example/", "video", "Video", "", 5000L)
        )

        server.seedFavorite(remoteItem("https://remote.example/", t = "Remote Only", at = 1500L))
        server.seedFavorite(remoteItem("https://both-wiki.example/", t = "Remote Wiki Title", at = 3000L))
        server.seedFavorite(remoteItem("https://both-fmhyx.example/", t = "New Fmhyx Name", at = 3000L))
        server.seedFavorite(remoteItem("https://local-newer.example/", t = "Old Remote", at = 2000L))

        val engine = newEngine()
        engine.sync(token)

        val localByUrl = userStore.bookmarks().associateBy { it.url }
        assertTrue("https://remote.example/" in localByUrl)
        // Wiki row keeps wiki name, updates savedAt
        assertEquals("Wiki Name", localByUrl["https://both-wiki.example/"]!!.name)
        assertEquals(3000L, localByUrl["https://both-wiki.example/"]!!.savedAt)
        // fmhyx: row takes remote name AND savedAt
        assertEquals("New Fmhyx Name", localByUrl["https://both-fmhyx.example/"]!!.name)
        assertEquals(3000L, localByUrl["https://both-fmhyx.example/"]!!.savedAt)

        // Server gets local-only and local-newer
        assertEquals(1000L, server.favorites["https://local.example/"]!!.longOrZero("at"))
        assertEquals(5000L, server.favorites["https://local-newer.example/"]!!.longOrZero("at"))
        assertEquals("Local Newer", server.favorites["https://local-newer.example/"]!!.str("t"))
    }

    @Test
    fun folderAdoptionByTrimmedNameCutTo60AndKeepExistingUnusedId() = runTest {
        val fAdopt = userStore.createFolder("  Movies & TV  ")
        val fLong = userStore.createFolder("x".repeat(65))
        val fUnused = userStore.createFolder("Local Only")
        userStore.setFolderSyncIds(mapOf(fUnused.id to "existing-unused-uuid"))

        server.seedCollection(remoteCollection(id = "col-adopt", name = "Movies & TV", items = emptyList(), updated = 500L))
        server.seedCollection(remoteCollection(id = "col-long", name = "x".repeat(60), items = emptyList(), updated = 500L))

        val engine = newEngine()
        engine.sync(token)

        val folders = userStore.folders().associateBy { it.id }
        assertEquals(3, folders.size)
        assertEquals("col-adopt", folders[fAdopt.id]!!.syncId)
        assertEquals("col-long", folders[fLong.id]!!.syncId)
        // Long folder must NOT be renamed back to 60 chars
        assertEquals("x".repeat(65), folders[fLong.id]!!.name)
        // Unused syncId was kept (not overwritten)
        assertEquals("existing-unused-uuid", folders[fUnused.id]!!.syncId)
        assertTrue("existing-unused-uuid" in server.collections)
    }

    @Test
    fun localRemovalReachesServerAndDoesNotComeBackExceptNewRemoteCollection() = runTest {
        userStore.upsertBookmark(UserBookmark("e1", "Site 1", "https://s1.example/", "video", "Video", "", 1000L))
        userStore.upsertBookmark(UserBookmark("e2", "Site 2", "https://s2.example/", "video", "Video", "", 1000L))
        val f1 = userStore.createFolder("Favs")
        userStore.moveToFolder("e2", f1.id)

        val engine = newEngine()
        engine.sync(token)

        // Locally remove both sites
        userStore.removeBookmark("e1")
        userStore.removeBookmark("e2")

        // Meanwhile another device adds https://s2.example/ to a NEW collection not in base.remote
        server.seedCollection(
            remoteCollection(
                id = "other-col",
                name = "From Web",
                items = listOf(remoteItem("https://s2.example/", t = "Site 2", at = 1000L)),
                updated = 2000L,
            )
        )

        engine.sync(token)
        assertNull(server.favorites["https://s1.example/"])
        assertNull(server.favorites["https://s2.example/"])
        // Removed from f1's collection (in base.remote), kept in other-col (not in base.remote)
        assertTrue(server.collections[userStore.folders().single { it.id == f1.id }.syncId]!!.collectionItems().isEmpty())
        assertEquals(1, server.collections["other-col"]!!.collectionItems().size)

        // Next sync: s1 does NOT come back; s2 comes back via other-col as collection-only!
        engine.sync(token)
        val byUrl = userStore.bookmarks().associateBy { it.url }
        assertFalse("https://s1.example/" in byUrl)
        assertTrue("https://s2.example/" in byUrl)
        assertNull(server.favorites["https://s2.example/"])
    }

    @Test
    fun remoteRemovalDeletesLocalOrDowngradesToCollectionOnly() = runTest {
        val f1 = userStore.createFolder("Keep")
        userStore.upsertBookmark(UserBookmark("e1", "Gone", "https://gone.example/", "video", "Video", "", 1000L))
        userStore.upsertBookmark(UserBookmark("e2", "InCol", "https://incol.example/", "video", "Video", "", 1000L, folderId = f1.id))
        userStore.upsertBookmark(UserBookmark("e3", "Resaved", "https://resaved.example/", "video", "Video", "", 1000L))

        val engine = newEngine()
        engine.sync(token)

        // Remote removes all 3 from favorites; e2 stays in collection; e3 is re-saved locally with newer savedAt
        server.favorites.clear()
        userStore.upsertBookmark(UserBookmark("e3", "Resaved", "https://resaved.example/", "video", "Video", "", 3000L))

        engine.sync(token)
        val byUrl = userStore.bookmarks().associateBy { it.url }
        assertFalse("https://gone.example/" in byUrl)
        assertTrue("https://incol.example/" in byUrl)
        assertNull(server.favorites["https://incol.example/"])
        assertTrue("https://resaved.example/" in byUrl)
        assertEquals(3000L, server.favorites["https://resaved.example/"]!!.longOrZero("at"))
    }

    @Test
    fun folderTombstoneBothDirectionsMarkerAndAbsentAndRevival() = runTest {
        val fLocalDel = userStore.createFolder("Delete Locally")
        val fRemoteMarker = userStore.createFolder("Delete Via Marker")
        val fRemoteAbsent = userStore.createFolder("Delete Absent")
        userStore.upsertBookmark(UserBookmark("e1", "Stay Saved", "https://stay.example/", "video", "Video", "", 1000L, folderId = fRemoteMarker.id))

        val engine = newEngine()
        engine.sync(token)

        val sidLocalDel = userStore.folders().first { it.id == fLocalDel.id }.syncId!!
        val sidMarker = userStore.folders().first { it.id == fRemoteMarker.id }.syncId!!
        val sidAbsent = userStore.folders().first { it.id == fRemoteAbsent.id }.syncId!!

        // Delete locally, delete remotely via marker, delete remotely via absence
        userStore.deleteFolder(fLocalDel.id)
        server.collections[sidMarker] = buildJsonObject {
            put("id", JsonPrimitive(sidMarker))
            put("name", JsonPrimitive(""))
            put("items", JsonArray(emptyList()))
            put("updated", JsonPrimitive(nowMs + 10L))
            put("deleted", JsonPrimitive(true))
        }
        server.collections.remove(sidAbsent)

        nowMs += 100L
        engine.sync(token)

        // Server received tombstone for sidLocalDel
        assertTrue(server.collections[sidLocalDel]!!.boolOrFalse("deleted"))
        // Local folders deleted; favorite bookmark stays saved with null folderId
        assertTrue(userStore.folders().isEmpty())
        val stay = userStore.bookmarks().single()
        assertEquals("https://stay.example/", stay.url)
        assertNull(stay.folderId)

        // Reviving a deleted marker: local creates folder keeping an old deleted syncId -> pushed with updated > marker
        val fRevive = userStore.createFolder("Revived")
        userStore.setFolderSyncIds(mapOf(fRevive.id to sidLocalDel))
        // Server marker has future timestamp relative to nowMs
        server.collections[sidLocalDel] = buildJsonObject {
            put("id", JsonPrimitive(sidLocalDel))
            put("name", JsonPrimitive(""))
            put("items", JsonArray(emptyList()))
            put("updated", JsonPrimitive(nowMs + 5000L))
            put("deleted", JsonPrimitive(true))
        }
        engine.sync(token)
        val revivedCol = server.collections[sidLocalDel]!!
        assertFalse(revivedCol.boolOrFalse("deleted"))
        assertEquals("Revived", revivedCol.str("name"))
        assertEquals(nowMs + 5001L, revivedCol.longOrZero("updated"))
    }

    @Test
    fun renameBothDirectionsAndLongSurrogateNameNotRenamedBack() = runTest {
        val longName = "a".repeat(59) + "\uD83D\uDE00extra" // 66 chars; cut to 59 chars without splitting surrogate
        val fLocal = userStore.createFolder("Start Local")
        val fRemote = userStore.createFolder("Start Remote")

        val engine = newEngine()
        engine.sync(token)
        val sidLocal = userStore.folders().first { it.id == fLocal.id }.syncId!!
        val sidRemote = userStore.folders().first { it.id == fRemote.id }.syncId!!

        // Rename local to >60 chars with emoji at boundary; rename remote on server
        userStore.renameFolder(fLocal.id, longName)
        server.collections[sidRemote] = remoteCollection(
            id = sidRemote,
            name = "Renamed On Server",
            items = emptyList(),
            updated = nowMs + 10L,
        )

        nowMs += 100L
        engine.sync(token)

        assertEquals("a".repeat(59), server.collections[sidLocal]!!.str("name"))
        assertEquals(longName, userStore.folders().first { it.id == fLocal.id }.name)
        assertEquals("Renamed On Server", userStore.folders().first { it.id == fRemote.id }.name)

        // Second run: no changes, no POST, long folder NOT renamed back to 59 chars
        val reqBefore = server.requests.size
        engine.sync(token)
        val newReqs = server.requests.drop(reqBefore)
        assertTrue(newReqs.all { it.method == "GET" })
        assertEquals(longName, userStore.folders().first { it.id == fLocal.id }.name)
    }

    @Test
    fun collectionOnlyLifecycleShownInFolderPromotedOnMoveOutAndFolderDeleteAndRemovedOnUnsave() = runTest {
        // 1. Seed 3 collection-only sites (not in favorites)
        server.seedCollection(
            remoteCollection(
                id = "c1",
                name = "Curated",
                items = listOf(
                    remoteItem("https://unsave.example/", t = "Unsave Me", at = 1000L),
                    remoteItem("https://moveout.example/", t = "Move Me Out", at = 1000L),
                    remoteItem("https://delfolder.example/", t = "Folder Del", at = 1000L),
                    remoteItem("https://remotedel.example/", t = "Remote Del", at = 1000L),
                ),
                updated = 1000L,
            )
        )

        val engine = newEngine()
        engine.sync(token)

        val folder = userStore.folders().single()
        assertEquals("Curated", folder.name)
        val local = userStore.bookmarks().associateBy { it.url }
        assertEquals(4, local.size)
        assertTrue(local.values.all { it.folderId == folder.id })
        assertTrue(server.favorites.isEmpty())

        // 2. Unsave first; move second out to unfiled; remove fourth remotely
        userStore.removeBookmark(local["https://unsave.example/"]!!.entryId)
        userStore.moveToFolder(local["https://moveout.example/"]!!.entryId, null)
        val updatedItems = server.collections["c1"]!!.collectionItems()
            .filter { it.str("u") != "https://remotedel.example/" }
        server.collections["c1"] = remoteCollection(
            id = "c1",
            name = "Curated",
            items = updatedItems,
            updated = nowMs + 10L,
        )

        nowMs += 100L
        engine.sync(token)

        // unsave.example removed from collection; moveout.example promoted to favorite and removed from collection;
        // remotedel.example deleted locally; delfolder.example still collection-only in Curated
        assertEquals(listOf("https://delfolder.example/"), server.collections["c1"]!!.collectionItems().map { it.str("u") })
        assertTrue("https://moveout.example/" in server.favorites)
        assertFalse("https://unsave.example/" in server.favorites)
        assertFalse("https://delfolder.example/" in server.favorites)
        val afterMove = userStore.bookmarks().associateBy { it.url }
        assertFalse("https://remotedel.example/" in afterMove)
        assertFalse("https://unsave.example/" in afterMove)
        assertNull(afterMove["https://moveout.example/"]!!.folderId)
        assertEquals(folder.id, afterMove["https://delfolder.example/"]!!.folderId)

        // 3. Delete Curated folder locally -> delfolder.example promoted to favorite and stays saved!
        userStore.deleteFolder(folder.id)
        nowMs += 100L
        engine.sync(token)

        assertTrue(server.collections["c1"]!!.boolOrFalse("deleted"))
        assertTrue("https://delfolder.example/" in server.favorites)
        val finalBm = userStore.bookmarks().associateBy { it.url }
        assertTrue("https://delfolder.example/" in finalBm)
        assertNull(finalBm["https://delfolder.example/"]!!.folderId)
    }

    @Test
    fun signOutAndInAgainKeepsCollectionOnlySitesOutOfWebFavorites() = runTest {
        server.seedCollection(
            remoteCollection("c1", "Curated", listOf(remoteItem("https://only.example/", at = 1000L)), updated = 1000L)
        )
        val engine = newEngine()
        engine.sync(token)

        engine.reset()
        nowMs += 100L
        engine.sync(token)

        assertTrue(server.favorites.isEmpty())
        assertEquals(userStore.folders().single().id, userStore.bookmarks().single().folderId)

        // Still collection-only: removing it on the web removes it from the phone.
        server.collections["c1"] = remoteCollection("c1", "Curated", emptyList(), updated = nowMs + 10L)
        nowMs += 100L
        engine.sync(token)
        assertTrue(userStore.bookmarks().isEmpty())
        assertTrue(server.favorites.isEmpty())
    }

    @Test
    fun collectionOnlySiteInNotYetSyncedFolderIsNotPromoted() {
        val url = "https://only.example/"
        val result = merge(
            local = BookmarksSnapshot(
                folders = listOf(Folder(id = 2L, name = "New", ord = 0, createdAt = 0L, syncId = null)),
                bookmarks = listOf(UserBookmark("fmhyx:1", "Only", url, "", "", "", 1000L, folderId = 2L)),
            ),
            base = BaseState(sites = mapOf(url to BaseSite(favAt = null, folders = setOf("c1"), remote = setOf("c1")))),
            remote = RemoteAccount(
                favorites = emptyList(),
                dislikes = emptyList(),
                collections = listOf(remoteCollection("c1", "Curated", listOf(remoteItem(url)))),
            ),
            excluded = emptySet(),
            now = 2000L,
        )
        assertTrue(result.remoteOps.favoritesToPush.isEmpty())
        assertTrue(result.localOps.deleteBookmarkUrls.isEmpty())
    }

    @Test
    fun multiFolderUrlShareUnknownFieldsAndSharedFolderIds() = runTest {
        val sharedItem = remoteItem("https://multi.example/", t = "Multi", at = 1000L, extra = "item_meta" to "keep")
        val otherOnly = remoteItem("https://other-only.example/", t = "Other", at = 1000L)
        server.seedFavorite(sharedItem)
        server.seedCollection(
            remoteCollection(
                id = "col-older",
                name = "Older",
                items = listOf(sharedItem),
                updated = 1000L,
                share = "share-older-abc",
                extra = "col_extra" to "v1",
            )
        )
        server.seedCollection(
            remoteCollection(
                id = "col-newer",
                name = "Newer",
                items = listOf(otherOnly, sharedItem),
                updated = 2000L,
                share = "share-newer-xyz",
                extra = "col_extra" to "v2",
            )
        )

        val engine = newEngine()
        engine.sync(token)

        val foldersBySync = userStore.folders().associateBy { it.syncId }
        val fOlder = foldersBySync["col-older"]!!
        val fNewer = foldersBySync["col-newer"]!!
        assertEquals(setOf(fOlder.id, fNewer.id), engine.sharedFolderIds())

        // Multi-folder item is shown in col-newer (greatest updated)
        val multiRow = userStore.bookmarks().first { it.url == "https://multi.example/" }
        assertEquals(fNewer.id, multiRow.folderId)

        // Add another bookmark into fOlder -> col-older is updated, multi.example is NOT dropped from col-older, share & extra survive!
        userStore.upsertBookmark(
            UserBookmark("e_new", "New In Older", "https://new-in-older.example/", "video", "Video", "", 3000L, folderId = fOlder.id)
        )
        nowMs += 100L
        engine.sync(token)

        val olderAfter = server.collections["col-older"]!!
        assertEquals("share-older-abc", olderAfter.str("share"))
        assertEquals("v1", olderAfter.str("col_extra"))
        assertEquals(
            listOf("https://multi.example/", "https://new-in-older.example/"),
            olderAfter.collectionItems().map { it.str("u") }
        )
        assertEquals("keep", olderAfter.collectionItems()[0].str("item_meta"))
        // col-newer untouched (still has both items in order)
        assertEquals(
            listOf("https://other-only.example/", "https://multi.example/"),
            server.collections["col-newer"]!!.collectionItems().map { it.str("u") }
        )

        // Reset clears sharedFolderIds
        engine.reset()
        assertTrue(engine.sharedFolderIds().isEmpty())
    }

    @Test
    fun dislikesNewerOlderAndCollectionOnlyInteraction() = runTest {
        val f = userStore.createFolder("Folder")
        userStore.upsertBookmark(
            UserBookmark("e_dis_new", "Disliked Newer", "https://dis-new.example/", "video", "Video", "", 1000L, folderId = f.id)
        )
        userStore.upsertBookmark(
            UserBookmark("e_dis_old", "Disliked Older", "https://dis-old.example/", "video", "Video", "", 1000L)
        )

        val engine = newEngine()
        engine.sync(token)
        val colId = userStore.folders().single().syncId!!

        // On server: dis-new moves to dislikes at 2000 (keeping collection item intact),
        // dis-old was disliked at 3000, and user re-saved dis-old locally at 5000 (> 3000)
        server.favorites.remove("https://dis-new.example/")
        server.seedDislike(remoteItem("https://dis-new.example/", at = 2000L))
        server.favorites.remove("https://dis-old.example/")
        server.seedDislike(remoteItem("https://dis-old.example/", at = 3000L))
        userStore.upsertBookmark(
            UserBookmark("e_dis_old", "Disliked Older", "https://dis-old.example/", "video", "Video", "", 5000L)
        )
        // Also a disliked URL inside a collection (not a favorite) must never be pulled locally
        server.seedDislike(remoteItem("https://dis-col.example/", at = 2000L))
        server.seedCollection(
            remoteCollection(
                id = "col-dis",
                name = "Has Disliked",
                items = listOf(remoteItem("https://dis-col.example/", t = "Nope", at = 1500L)),
                updated = 2000L,
            )
        )

        nowMs += 100L
        engine.sync(token)

        val localUrls = userStore.bookmarks().map { it.url }.toSet()
        // Newer dislike deleted local row, pushed no favorite, and did NOT remove from server collection
        assertFalse("https://dis-new.example/" in localUrls)
        assertFalse("https://dis-new.example/" in server.favorites)
        assertEquals(listOf("https://dis-new.example/"), server.collections[colId]!!.collectionItems().map { it.str("u") })
        // Older dislike overridden by newer local favorite (5000 > 3000)
        assertTrue("https://dis-old.example/" in localUrls)
        assertTrue("https://dis-old.example/" in server.favorites)
        // Disliked URL in collection is never pulled locally
        assertFalse("https://dis-col.example/" in localUrls)
    }

    @Test
    fun exactUrlRoundTripTrailingSlashQueryFragmentAndNonAscii() = runTest {
        val urls = listOf(
            "https://example.com/path",
            "https://example.com/path/",
            "https://example.com/search?b=2&a=1&utm_source=keep#frag-ümlaut",
            "https://exämple.org/café/путь?q=1#sec",
        )
        urls.forEachIndexed { idx, u ->
            userStore.upsertBookmark(
                UserBookmark("id_$idx", "Site $idx", u, "video", "Video", "", 1000L + idx)
            )
        }
        val engine = newEngine()
        engine.sync(token)
        assertEquals(urls.toSet(), server.favorites.keys)

        // Fresh local store + reset pulls exact same URLs byte-for-byte
        val freshDb = tempFolder.newFile("fresh_user.db")
        val freshStore = UserStore(freshDb, clock = { nowMs })
        try {
            val freshEngine = FmhyXSyncEngine(freshStore, client, DatasetSyncEntryResolver(datasetStore))
            freshEngine.sync(token)
            assertEquals(urls.toSet(), freshStore.bookmarks().map { it.url }.toSet())
        } finally {
            freshStore.close()
        }
    }

    @Test
    fun nsfwAndBrowserBlankSectionRowsAndNonHttpOrLongUrlsExcluded() = runTest {
        seedDataset(
            entries = listOf(
                TestEntry("wiki_sfw", "privacy", "privacy/dns", "Known SFW", "https://known-sfw.example/", description = "Safe"),
                TestEntry("wiki_later_gone", "video", null, "Later Gone", "https://later-gone.example/"),
            )
        )
        nsfwList = listOf("https://blank-nsfw.example/")

        val folder = userStore.createFolder("Mixed")
        // Excluded: explicit nsfw section
        userStore.upsertBookmark(
            UserBookmark("nsfw_1", "NSFW Section", "https://nsfw-sec.example/", "nsfw", "NSFW", "", 1000L, folderId = folder.id)
        )
        // Excluded: blank section, unknown entryId
        userStore.upsertBookmark(
            UserBookmark("tab_unknown", "Random Tab", "https://random-tab.example/", "", "", "", 1000L, folderId = folder.id)
        )
        // Excluded: blank section, URL in nsfwUrls()
        userStore.upsertBookmark(
            UserBookmark("wiki_sfw_alias", "NSFW URL", "https://blank-nsfw.example/", "", "", "", 1000L, folderId = folder.id)
        )
        // Excluded per live API (a): non-http(s) and >2048 URLs stay phone-only, folder unaffected
        userStore.upsertBookmark(
            UserBookmark("ftp_1", "FTP", "ftp://files.example/archive.zip", "video", "Video", "", 1000L, folderId = folder.id)
        )
        val tooLongUrl = "https://long.example/" + "a".repeat(2040)
        userStore.upsertBookmark(
            UserBookmark("long_1", "Long URL", tooLongUrl, "video", "Video", "", 1000L, folderId = folder.id)
        )
        // Synced: blank section, known SFW entryId
        userStore.upsertBookmark(
            UserBookmark("wiki_sfw", "Known SFW", "https://known-sfw.example/", "", "", "", 1000L, folderId = folder.id)
        )
        // Synced: blank section, known now, will disappear from dataset on Run 2
        userStore.upsertBookmark(
            UserBookmark("wiki_later_gone", "Later Gone", "https://later-gone.example/", "", "", "", 1000L, folderId = folder.id)
        )

        // Seed remote collection containing an excluded URL too (must stay untouched in remote collection!)
        val colId = "col-mixed"
        userStore.setFolderSyncIds(mapOf(folder.id to colId))
        server.seedCollection(
            remoteCollection(
                id = colId,
                name = "Mixed",
                items = listOf(remoteItem("https://nsfw-sec.example/", t = "Should Stay Remote", at = 500L)),
                updated = 500L,
            )
        )

        val engine = newEngine()
        engine.sync(token)

        assertEquals(setOf("https://known-sfw.example/", "https://later-gone.example/"), server.favorites.keys)
        // Excluded remote item in collection left untouched; phone-only items not pushed
        val colUrls = server.collections[colId]!!.collectionItems().map { it.str("u") }.toSet()
        assertEquals(
            setOf("https://nsfw-sec.example/", "https://known-sfw.example/", "https://later-gone.example/"),
            colUrls
        )
        // Local excluded & phone-only rows still in their folder untouched
        val localByUrl = userStore.bookmarks().associateBy { it.url }
        assertEquals(folder.id, localByUrl["ftp://files.example/archive.zip"]!!.folderId)
        assertEquals(folder.id, localByUrl[tooLongUrl]!!.folderId)
        assertEquals(folder.id, localByUrl["https://nsfw-sec.example/"]!!.folderId)
        assertEquals(folder.id, localByUrl["https://random-tab.example/"]!!.folderId)

        // Remove wiki_later_gone from dataset; because it is already in base, it stays synced!
        datasetStore.withConnection { conn ->
            conn.execSQL("DELETE FROM entries WHERE id = 'wiki_later_gone';")
        }
        // Update its savedAt locally and sync again -> must still push update!
        userStore.upsertBookmark(
            UserBookmark("wiki_later_gone", "Later Gone", "https://later-gone.example/", "", "", "", 5000L, folderId = folder.id)
        )
        nowMs += 100L
        engine.sync(token)
        assertEquals(5000L, server.favorites["https://later-gone.example/"]!!.longOrZero("at"))
    }

    @Test
    fun failingDatasetQueryAbortsSyncWithoutTouchingLocalDataOrBase() = runTest {
        userStore.upsertBookmark(UserBookmark("e1", "Keep Me", "https://keep.example/", "video", "Video", "", 1000L))
        val failingResolver = object : SyncEntryResolver {
            override suspend fun byIds(ids: Collection<String>): Map<String, ResolvedEntry> =
                throw IllegalStateException("Dataset corrupted")

            override suspend fun byUrls(urls: Collection<String>): Map<String, List<ResolvedEntry>> = emptyMap()
            override suspend fun nsfwUrls(): Set<String> = emptySet()
        }
        val engine = newEngine(resolver = failingResolver)
        try {
            engine.sync(token)
            fail("Expected dataset exception to abort sync")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(1, userStore.bookmarks().size)
        assertNull(userStore.readFmhyXBase())
    }

    @Test
    fun outgoingSanitizationForItemsAndCollectionsMatchesLiveApiRules() = runTest {
        seedDataset(
            entries = listOf(
                TestEntry(
                    id = "e_meta",
                    sectionId = "s".repeat(201),
                    headingId = "sec/" + "h".repeat(201),
                    name = "Title",
                    url = "https://meta-over.example/",
                    description = "x".repeat(239) + "\uD83D\uDE00tail",
                ),
            )
        )
        val fBlank = userStore.createFolder("   ")
        userStore.upsertBookmark(
            UserBookmark("e_blank_t", "   ", "https://blank-name.example/", "video", "Video", "", 1000L, folderId = fBlank.id)
        )
        // Duplicate URL in the same folder across two entryIds -> collection items must deduplicate by u!
        userStore.upsertBookmark(
            UserBookmark("dup_1", "Dup 1", "https://dup.example/", "video", "Video", "", 1000L, folderId = fBlank.id)
        )
        userStore.upsertBookmark(
            UserBookmark("dup_2", "Dup 2", "https://dup.example/", "privacy", "Privacy", "", 2000L, folderId = fBlank.id)
        )
        userStore.upsertBookmark(
            UserBookmark("e_meta", "A".repeat(299) + "\uD83D\uDE00Z", "https://meta-over.example/", "s".repeat(201), "", "", 1000L)
        )

        val engine = newEngine()
        engine.sync(token)

        val col = server.collections.values.single()
        assertEquals("Folder", col.str("name"))
        val colItems = col.collectionItems()
        val colUrls = colItems.map { it.str("u") }
        assertEquals(setOf("https://blank-name.example/", "https://dup.example/"), colUrls.toSet())
        assertEquals(2, colUrls.size)
        val blankColItem = colItems.single { it.str("u") == "https://blank-name.example/" }
        assertEquals("https://blank-name.example/", blankColItem.str("t"))

        val favMeta = server.favorites["https://meta-over.example/"]!!
        assertEquals("A".repeat(299), favMeta.str("t"))
        assertEquals("x".repeat(239), favMeta.str("d"))
        assertEquals("", favMeta.str("p")) // blank/over-200 p omitted on wire; server normalizes missing p to ""
        assertFalse("h" in favMeta)

        // Second sync should be a clean no-op despite server normalizing missing p to ""
        val reqBefore = server.requests.size
        engine.sync(token)
        assertTrue(server.requests.drop(reqBefore).all { it.method == "GET" })
    }

    @Test
    fun rejectedPushNeverDeletesLocalData() = runTest {
        val f = userStore.createFolder("Rejected Folder")
        userStore.upsertBookmark(
            UserBookmark("e1", "Rejected Fav", "https://rejected.example/", "video", "Video", "", 1000L, folderId = f.id)
        )

        // Server accepts POST /api/v1/sync with 200, but returns empty favorites/collections (silently dropped)
        val dropServer = FakeFmhyXServer(validToken = token)
        primary.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "POST" && request.target == "/api/v1/sync") {
                    return MockResponse.Builder()
                        .code(200)
                        .body("""{"favorites":[],"dislikes":[],"collections":[]}""")
                        .build()
                }
                return dropServer.dispatch(request)
            }
        }

        val engine = newEngine()
        engine.sync(token)

        assertEquals(1, userStore.bookmarks().size)
        assertEquals(1, userStore.folders().size)
        // Rejected items must NOT be recorded in base, so next run retries them
        val baseJson = userStore.readFmhyXBase()!!
        assertFalse(baseJson.contains("https://rejected.example/"))
        assertFalse(baseJson.contains(userStore.folders().single().syncId!!))
    }

    @Test
    fun emptySecondAccountAfterResetDeletesNothingAndPushesLocal() = runTest {
        val f = userStore.createFolder("My Folder")
        userStore.upsertBookmark(
            UserBookmark("e1", "Site 1", "https://site1.example/", "video", "Video", "", 1000L, folderId = f.id)
        )

        val engine = newEngine()
        engine.sync(token)
        assertEquals(1, server.favorites.size)
        assertEquals(1, server.collections.size)

        // Sign out -> reset -> switch to empty account
        engine.reset()
        assertNull(userStore.readFmhyXBase())

        val accountB = FakeFmhyXServer(validToken = "token_account_b")
        primary.dispatcher = accountB
        mirror.dispatcher = accountB

        engine.sync("token_account_b")
        assertEquals(1, userStore.bookmarks().size)
        assertEquals(1, userStore.folders().size)
        assertTrue("https://site1.example/" in accountB.favorites)
        assertEquals(1, accountB.collections.size)
    }

    @Test
    fun concurrentLocalChangeDuringSyncIsPreservedAndSetsChangesPending() = runTest {
        userStore.upsertBookmark(UserBookmark("e1", "Site 1", "https://s1.example/", "video", "Video", "", 1000L))
        val f = userStore.createFolder("Orig")
        val engine = newEngine()
        engine.sync(token)
        val fSyncId = userStore.folders().single().syncId!!

        // Remote wants to delete e1 and rename f -> "RemoteRename"
        server.favorites.remove("https://s1.example/")
        server.collections[fSyncId] = remoteCollection(fSyncId, "RemoteRename", emptyList(), updated = nowMs + 10L)

        // While pull is in flight, user re-saves e1 with newer at, renames folder to "UserRename", and adds e2
        var mutated = false
        server.nextHook = { req ->
            if (!mutated && req.method == "GET" && req.target == "/api/v1/sync") {
                mutated = true
                runBlocking {
                    userStore.upsertBookmark(
                        UserBookmark("e1", "Site 1", "https://s1.example/", "video", "Video", "", 9999L)
                    )
                    userStore.renameFolder(f.id, "UserRename")
                    userStore.upsertBookmark(
                        UserBookmark("e2", "Site 2", "https://s2.example/", "video", "Video", "", 9999L, folderId = f.id)
                    )
                }
            }
            null
        }

        nowMs += 100L
        val res1 = engine.sync(token)
        assertTrue(res1.changesPending)

        // User's concurrent edits MUST be preserved (delete e1 and rename f skipped)
        val bookmarksAfter1 = userStore.bookmarks().associateBy { it.url }
        assertTrue("https://s1.example/" in bookmarksAfter1)
        assertEquals(9999L, bookmarksAfter1["https://s1.example/"]!!.savedAt)
        assertTrue("https://s2.example/" in bookmarksAfter1)
        assertEquals("UserRename", userStore.folders().single().name)

        // Next sync pushes all concurrent edits and settles with changesPending = false
        nowMs += 100L
        val res2 = engine.sync(token)
        assertFalse(res2.changesPending)
        assertEquals(9999L, server.favorites["https://s1.example/"]!!.longOrZero("at"))
        assertEquals(9999L, server.favorites["https://s2.example/"]!!.longOrZero("at"))
        assertEquals("UserRename", server.collections[fSyncId]!!.str("name"))
    }

    @Test
    fun consecutiveNoOpRunSendsNoPostAndDoesNotBumpChanges() = runTest {
        userStore.upsertBookmark(UserBookmark("e1", "Site 1", "https://s1.example/", "video", "Video", "", 1000L))
        val engine = newEngine()
        engine.sync(token)

        val changesBefore = userStore.changes.value
        val reqsBefore = server.requests.size
        val res2 = engine.sync(token)
        assertFalse(res2.changesPending)
        assertEquals(changesBefore, userStore.changes.value)
        val newReqs = server.requests.drop(reqsBefore)
        assertEquals(1, newReqs.size)
        assertEquals("GET", newReqs[0].method)
        assertEquals("/api/v1/sync", newReqs[0].path)
    }

    @Test
    fun networkAndHttpErrorsLeaveLocalDataAndBaseUntouched() = runTest {
        userStore.upsertBookmark(UserBookmark("e1", "Site 1", "https://s1.example/", "video", "Video", "", 1000L))
        val engine = newEngine()
        engine.sync(token)
        val baseBefore = userStore.readFmhyXBase()
        val bookmarksBefore = userStore.bookmarks()

        // Remote adds a new bookmark and local has a pending change that would normally run
        server.seedFavorite(remoteItem("https://remote-new.example/", t = "Remote", at = 5000L))

        val errorCases: List<Triple<MockResponse, Int, Class<out FmhyXException>>> = listOf(
            Triple(MockResponse.Builder().code(401).build(), 1, FmhyXException.Unauthorized::class.java),
            Triple(MockResponse.Builder().code(429).setHeader("Retry-After", "10").build(), 1, FmhyXException.RateLimited::class.java),
            Triple(MockResponse.Builder().code(502).build(), 2, FmhyXException.Server::class.java),
            Triple(MockResponse.Builder().code(200).body("{bad json").build(), 1, FmhyXException.BadResponse::class.java),
        )

        for ((resp, count, expectedEx) in errorCases) {
            repeat(count) { server.enqueueOverride(resp) }
            try {
                engine.sync(token)
                fail("Expected ${expectedEx.simpleName}")
            } catch (e: FmhyXException) {
                assertTrue("Expected ${expectedEx.simpleName}, got ${e::class.java.simpleName}", expectedEx.isInstance(e))
            }
            assertEquals(baseBefore, userStore.readFmhyXBase())
            assertEquals(bookmarksBefore, userStore.bookmarks())
        }

        // Offline (both servers drop connection) leaves local data and base untouched
        val offlineHook: (RecordedRequest) -> MockResponse = {
            MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
        }
        server.nextHook = offlineHook
        try {
            engine.sync(token)
            fail("Expected Offline")
        } catch (_: FmhyXException.Offline) {
            // expected
        } finally {
            server.nextHook = null
        }
        assertEquals(baseBefore, userStore.readFmhyXBase())
        assertEquals(bookmarksBefore, userStore.bookmarks())
    }

    @Test
    fun mirrorFallbackWorksInsideFullEngineSync() = runTest {
        userStore.upsertBookmark(UserBookmark("e1", "Site 1", "https://s1.example/", "video", "Video", "", 1000L))
        primary.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse.Builder().code(503).build()
        }
        val engine = newEngine()
        engine.sync(token)
        assertTrue("https://s1.example/" in server.favorites)
    }

    @Test
    fun clientSideLimitsThrowBeforeSendingAndLeaveLocalAndBaseUntouched() = runTest {
        val engine = newEngine()

        // 1. SAVED_ITEMS > 5000
        for (i in 1..5000) {
            server.seedFavorite(remoteItem("https://r$i.example/", at = 1000L))
        }
        userStore.upsertBookmark(UserBookmark("e_over", "Over", "https://over.example/", "video", "Video", "", 2000L))
        assertLimitViolation(engine, SyncLimit.SAVED_ITEMS)
        userStore.removeBookmark("e_over")
        server.favorites.clear()

        // 2. FOLDERS > 50
        for (i in 1..50) {
            server.seedCollection(remoteCollection("col-$i", "Col $i", emptyList()))
        }
        val f51 = userStore.createFolder("Folder 51")
        assertLimitViolation(engine, SyncLimit.FOLDERS)
        userStore.deleteFolder(f51.id)
        server.collections.clear()

        // 3. FOLDER_ITEMS > 500
        val bigFolder = userStore.createFolder("Huge")
        for (i in 1..501) {
            userStore.upsertBookmark(
                UserBookmark("item_$i", "Item $i", "https://item$i.example/", "video", "Video", "", 1000L + i, folderId = bigFolder.id)
            )
        }
        assertLimitViolation(engine, SyncLimit.FOLDER_ITEMS)

        // Clean up and check BODY_SIZE > 20 MiB using custom huge remote response + local collection
        userStore.deleteFolder(bigFolder.id)
        for (i in 1..501) {
            userStore.removeBookmark("item_$i")
        }
        val bigCol = userStore.createFolder("BigBody")
        userStore.setFolderSyncIds(mapOf(bigCol.id to "col-big-body"))
        // Seed a remote collection with 100 items each having huge unknown field (~250 KiB each => >20 MiB total)
        val hugePad = "p".repeat(250 * 1024)
        val remoteItems = (1..90).map { idx ->
            remoteItem("https://pad$idx.example/", t = "Pad $idx", at = 1000L, extra = "pad" to hugePad)
        }
        server.seedCollection(remoteCollection("col-big-body", "BigBody", remoteItems, updated = 1000L))
        // Add 1 local bookmark into BigBody to trigger pushing the >20 MiB collection
        userStore.upsertBookmark(
            UserBookmark("trigger", "Trigger", "https://trigger.example/", "video", "Video", "", 2000L, folderId = bigCol.id)
        )
        assertLimitViolation(engine, SyncLimit.BODY_SIZE)
    }

    private suspend fun assertLimitViolation(engine: FmhyXSyncEngine, expected: SyncLimit) {
        val baseBefore = userStore.readFmhyXBase()
        val reqsBefore = server.requests.size
        try {
            engine.sync(token)
            fail("Expected LimitExceeded($expected)")
        } catch (e: FmhyXException.LimitExceeded) {
            assertEquals(expected, e.limit)
        }
        assertEquals(baseBefore, userStore.readFmhyXBase())
        val sentWrites = server.requests.drop(reqsBefore).filter { it.method != "GET" }
        assertTrue("Expected zero POST requests on limit failure, got $sentWrites", sentWrites.isEmpty())
    }
}
