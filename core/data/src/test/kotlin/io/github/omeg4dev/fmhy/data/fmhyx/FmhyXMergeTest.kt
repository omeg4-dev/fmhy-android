package io.github.omeg4dev.fmhy.data.fmhyx

import io.github.omeg4dev.fmhy.data.user.BookmarksSnapshot
import io.github.omeg4dev.fmhy.data.user.Folder
import io.github.omeg4dev.fmhy.data.user.UserBookmark
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FmhyXMergeTest {

    private fun bookmark(
        entryId: String,
        url: String,
        name: String = "Site",
        sectionId: String = "video",
        savedAt: Long = 1000L,
        folderId: Long? = null,
    ) = UserBookmark(
        entryId = entryId,
        name = name,
        url = url,
        sectionId = sectionId,
        sectionTitle = "Video",
        headingPath = "Video > Movies",
        savedAt = savedAt,
        note = "private note",
        folderId = folderId,
    )

    private fun favJson(u: String, at: Long, t: String = "Remote", extra: String? = null): JsonObject = buildJsonObject {
        put("u", JsonPrimitive(u))
        put("t", JsonPrimitive(t))
        put("at", JsonPrimitive(at))
        if (extra != null) put("custom_field", JsonPrimitive(extra))
    }

    @Test
    fun cutUtf16SafeNeverSplitsSurrogatePair() {
        val emoji = "\uD83D\uDE00" // 2 UTF-16 code units
        val atBoundary = "a".repeat(59) + emoji // length 61
        val cut = cutUtf16Safe(atBoundary, 60)
        assertEquals(59, cut.length)
        assertEquals("a".repeat(59), cut)
        assertFalse(Character.isHighSurrogate(cut.last()))

        val fits = "a".repeat(58) + emoji // length 60
        assertEquals(fits, cutUtf16Safe(fits, 60))
        assertEquals("Folder", outgoingCollectionName("   "))
    }

    @Test
    fun allTenSiteTableCasesAndMultiRowUrl() {
        val f1 = Folder(id = 1L, name = "F1", ord = 0, createdAt = 100L, syncId = "c1")
        val f2 = Folder(id = 2L, name = "F2", ord = 1, createdAt = 200L, syncId = "c2")

        // 1: L=yes, B=none, R=nothing -> push favorite
        val b1 = bookmark("e1", "https://1.example/", savedAt = 1000L)
        // 2: L=yes, B=none, R=fav (remote newer) -> update local savedAt (and name for fmhyx:)
        val b2 = bookmark("fmhyx:2222", "https://2.example/", name = "OldName", savedAt = 1000L)
        // 3: L=yes, B=none, R=only in collection -> push favorite
        val b3 = bookmark("e3", "https://3.example/", savedAt = 1000L)
        // 5: L=no, B=in base, R=fav unchanged -> remove favorite + remove from base.remote
        // 6: L=no, B=in base, R=fav newer -> pull back
        // 8a: L=yes, B=favAt(1000), R=not fav, localAt(2000) > base -> push
        val b8a = bookmark("e8a", "https://8a.example/", savedAt = 2000L)
        // 8b: L=yes, B=favAt(1000), R=not fav, in collection -> keep (becomes collection-only)
        val b8b = bookmark("e8b", "https://8b.example/", savedAt = 1000L, folderId = 1L)
        // 8c: L=yes, B=favAt(1000), R=not fav, not in collection -> delete local rows
        val b8c = bookmark("e8c", "https://8c.example/", savedAt = 1000L)
        // 9: L=yes, B=collection-only, R=fav(1000), localAt == 1000 -> becomes fav, nothing pushed
        val b9 = bookmark("e9", "https://9.example/", savedAt = 1000L, folderId = 1L)
        // 10a: L=yes, B=collection-only, R=not fav, user left unfiled -> push fav
        val b10a = bookmark("e10a", "https://10a.example/", savedAt = 1000L, folderId = null)
        // 10b: L=yes, B=collection-only, R=not fav, in no remote col and folders unchanged -> delete
        val b10b = bookmark("e10b", "https://10b.example/", savedAt = 1000L, folderId = 1L)

        val snapshot = BookmarksSnapshot(
            folders = listOf(f1, f2),
            bookmarks = listOf(b1, b2, b3, b8a, b8b, b8c, b9, b10a, b10b),
        )
        val base = BaseState(
            sites = mapOf(
                "https://5.example/" to BaseSite(favAt = 1000L, folders = setOf("c1"), remote = setOf("c1")),
                "https://6.example/" to BaseSite(favAt = 1000L),
                "https://8a.example/" to BaseSite(favAt = 1000L),
                "https://8b.example/" to BaseSite(favAt = 1000L, folders = setOf("c1"), remote = setOf("c1")),
                "https://8c.example/" to BaseSite(favAt = 1000L),
                "https://9.example/" to BaseSite(favAt = null, folders = setOf("c1"), remote = setOf("c1")),
                "https://10a.example/" to BaseSite(favAt = null, folders = setOf("c1"), remote = setOf("c1")),
                "https://10b.example/" to BaseSite(favAt = null, folders = setOf("c1"), remote = setOf("c1")),
            ),
            collections = mapOf(
                "c1" to BaseCollection("F1", "F1"),
                "c2" to BaseCollection("F2", "F2"),
            ),
        )
        val remote = RemoteAccount(
            favorites = listOf(
                favJson("https://2.example/", 2000L, t = "NewRemoteName"),
                favJson("https://4.example/", 3000L, t = "Remote4"),
                favJson("https://5.example/", 1000L),
                favJson("https://6.example/", 2500L, t = "PullBack6"),
                favJson("https://9.example/", 1000L),
            ),
            dislikes = emptyList(),
            collections = listOf(
                buildJsonObject {
                    put("id", JsonPrimitive("c1"))
                    put("name", JsonPrimitive("F1"))
                    put(
                        "items",
                        JsonArray(
                            listOf(
                                favJson("https://3.example/", 1000L),
                                favJson("https://5.example/", 1000L),
                                favJson("https://8b.example/", 1000L),
                                favJson("https://9.example/", 1000L),
                                favJson("https://10a.example/", 1000L),
                            )
                        )
                    )
                    put("updated", JsonPrimitive(1000L))
                },
                buildJsonObject {
                    put("id", JsonPrimitive("c2"))
                    put("name", JsonPrimitive("F2"))
                    put("items", JsonArray(emptyList()))
                    put("updated", JsonPrimitive(1000L))
                },
            ),
        )

        val result = merge(snapshot, base, remote, excluded = emptySet(), now = 5000L)

        assertEquals(
            setOf("https://1.example/", "https://3.example/", "https://8a.example/", "https://10a.example/"),
            result.remoteOps.favoritesToPush.map { it.str("u") }.toSet()
        )
        assertEquals(setOf("https://5.example/"), result.remoteOps.favoritesToRemove.map { it.str("u") }.toSet())
        assertEquals(
            setOf("https://8c.example/", "https://10b.example/"),
            result.localOps.deleteBookmarkUrls
        )
        assertEquals(
            setOf("https://4.example/", "https://6.example/"),
            result.localOps.addBookmarks.map { it.bookmark.url }.toSet()
        )
        val upd2 = result.localOps.updateBookmarks.single { it.entryId == "fmhyx:2222" }
        assertEquals(2000L, upd2.savedAt)
        assertEquals("NewRemoteName", upd2.name)

        // c1 should remove https://5.example/ and https://10a.example/ and keep https://3.example/, https://8b.example/, https://9.example/
        val c1Push = result.remoteOps.collectionsToPush.single { it.str("id") == "c1" }
        val c1Urls = c1Push.collectionItems().map { it.str("u") }.toSet()
        assertEquals(setOf("https://3.example/", "https://8b.example/", "https://9.example/"), c1Urls)
        assertEquals(5000L, c1Push.longOrZero("updated"))
        assertTrue(result.remoteOps.collectionsToPush.none { it.str("id") == "c2" })
    }

    @Test
    fun outgoingItemSanitizesFieldsAndKeepsServerUnknownFields() {
        val row = bookmark(
            entryId = "e1",
            url = "https://site.example/",
            name = "   ",
            sectionId = "s".repeat(201),
            savedAt = 1234L,
        )
        val resolved = ResolvedEntry(
            entryId = "e1",
            url = "https://site.example/",
            originalUrl = null,
            name = "Resolved Name",
            sectionId = "p".repeat(201),
            sectionTitle = "Sec",
            headingId = "sec/" + "h".repeat(201),
            headingPath = "Sec > H",
            description = "d".repeat(239) + "\uD83D\uDE00",
        )
        val remoteExisting = favJson("https://site.example/", 100L, extra = "preserve-me")
        val item = buildSanitizedItem(row, 1234L, resolved, remoteExisting)

        assertEquals("https://site.example/", item.str("u"))
        assertEquals("https://site.example/", item.str("t"))
        assertEquals(1234L, item.longOrZero("at"))
        assertFalse("p" in item)
        assertFalse("h" in item)
        assertEquals("d".repeat(239), item.str("d"))
        assertEquals("preserve-me", item.str("custom_field"))
    }
}
