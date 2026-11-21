package io.github.omeg4dev.fmhy.data.newbadge

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Tier
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NewBadgeStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fixedToday = LocalDate.parse("2026-11-20")

    private fun sampleEntry(
        sectionId: String = "audio",
        url: String = "https://example.com/site",
    ): Entry = Entry(
        id = "e1",
        num = 1,
        sectionId = sectionId,
        headingId = null,
        ord = 1,
        name = "Sample",
        url = url,
        originalUrl = null,
        description = "",
        tags = emptyList(),
        headingPath = "",
        tier = Tier.NORMAL,
        kind = EntryKind.NORMAL,
        line = 1,
        isInlineMore = false,
        isBase64 = false,
        mirrors = emptyList(),
        extraLinks = emptyList(),
        firstSeen = 0L,
        lastChanged = 0L,
        starredAt = null,
    )

    @Test
    fun missingFile_loadsEmptyIndex() {
        val dir = tempFolder.newFolder()
        val store = NewBadgeStore(dir)
        assertEquals(NewBadgeIndex.EMPTY, store.index.value)
    }

    @Test
    fun corruptFile_loadsEmptyIndex() {
        val dir = tempFolder.newFolder()
        val file = File(dir, "new-badges.json")
        file.writeText("{ this is corrupt json !!!")
        val logged = mutableListOf<String>()
        val store = NewBadgeStore(dir, log = { logged.add(it) })
        assertEquals(NewBadgeIndex.EMPTY, store.index.value)
        assertTrue(logged.any { it.contains("failed to load state") })
    }

    @Test
    fun versionNotOne_loadsEmptyIndex() {
        val dir = tempFolder.newFolder()
        val file = File(dir, "new-badges.json")
        file.writeText("""{"version":2,"checkedAt":0,"fetchedAt":0,"pages":{}}""")
        val logged = mutableListOf<String>()
        val store = NewBadgeStore(dir, log = { logged.add(it) })
        assertEquals(NewBadgeIndex.EMPTY, store.index.value)
        assertTrue(logged.any { it.contains("unsupported version 2") })
    }

    @Test
    fun atomicWrite_writesFileAndUpdatesIndex() {
        val dir = tempFolder.newFolder()
        val store = NewBadgeStore(dir, clock = { fixedToday })
        val file = File(dir, "new-badges.json")
        val tmpFile = File(dir, "new-badges.json.tmp")

        val state = NewBadgeState(
            version = 1,
            checkedAt = 1000L,
            fetchedAt = 1000L,
            pages = mapOf(
                "audio" to PageState(
                    mdSha = "md1",
                    bodySha = "b1",
                    dirty = 0,
                    fetchedAt = 1000L,
                    marks = listOf(BadgeMark("example.com/site", "2026-11-18")),
                ),
            ),
        )

        store.save(state)

        assertTrue(file.exists())
        assertFalse(tmpFile.exists())
        val entry = sampleEntry()
        assertEquals(LocalDate.parse("2026-11-18"), store.index.value.activeDate(entry, fixedToday))
    }

    @Test
    fun pruning_removesMarksOlderThan30Days() {
        val dir = tempFolder.newFolder()
        val store = NewBadgeStore(dir, clock = { fixedToday })

        // fixedToday is 2026-11-20
        // 30 days ago is 2026-10-21 (kept)
        // 31 days ago is 2026-10-20 (pruned)
        val state = NewBadgeState(
            version = 1,
            pages = mapOf(
                "audio" to PageState(
                    marks = listOf(
                        BadgeMark("example.com/fresh", "2026-11-19"),
                        BadgeMark("example.com/day30", "2026-10-21"),
                        BadgeMark("example.com/day31", "2026-10-20"),
                    ),
                ),
            ),
        )

        store.save(state)

        val loaded = store.loadState()
        val marks = loaded.pages["audio"]?.marks.orEmpty()
        assertEquals(2, marks.size)
        val urls = marks.map { it.u }.toSet()
        assertTrue(urls.contains("example.com/fresh"))
        assertTrue(urls.contains("example.com/day30"))
        assertFalse(urls.contains("example.com/day31"))
    }

    @Test
    fun pruning_duplicateKeysKeepLatestDate() {
        val dir = tempFolder.newFolder()
        val store = NewBadgeStore(dir, clock = { fixedToday })

        val state = NewBadgeState(
            version = 1,
            pages = mapOf(
                "audio" to PageState(
                    marks = listOf(
                        BadgeMark("rentry.co/FMHYB64#a", "2026-11-15"),
                        BadgeMark("rentry.co/FMHYB64#a", "2026-11-19"),
                    ),
                ),
            ),
        )

        store.save(state)

        val loaded = store.loadState()
        val audioMarks = loaded.pages["audio"]?.marks.orEmpty()
        assertEquals(1, audioMarks.size)
        assertEquals("rentry.co/FMHYB64#a", audioMarks[0].u)
        assertEquals("2026-11-19", audioMarks[0].d)
    }

    @Test
    fun pruning_removesInvalidSectionsWhenProvided() {
        val dir = tempFolder.newFolder()
        val store = NewBadgeStore(dir, clock = { fixedToday })

        val state = NewBadgeState(
            version = 1,
            pages = mapOf(
                "audio" to PageState(marks = listOf(BadgeMark("example.com/audio", "2026-11-19"))),
                "video" to PageState(marks = listOf(BadgeMark("example.com/video", "2026-11-19"))),
                "obsolete" to PageState(marks = listOf(BadgeMark("example.com/obsolete", "2026-11-19"))),
            ),
        )

        store.save(state, validSections = setOf("audio", "video"))

        val loaded = store.loadState()
        assertEquals(setOf("audio", "video"), loaded.pages.keys)
        assertNull(store.index.value.activeDate(sampleEntry(sectionId = "obsolete", url = "https://example.com/obsolete"), fixedToday))
    }
}
