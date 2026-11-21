package io.github.omeg4dev.fmhy.data.newbadge

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.newbadge.NewBadgeStore.Companion.toIndex
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewBadgeIndexTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun sampleEntry(
        sectionId: String = "audio",
        url: String = "https://example.com/site",
        originalUrl: String? = null,
    ): Entry = Entry(
        id = "e1",
        num = 1,
        sectionId = sectionId,
        headingId = null,
        ord = 1,
        name = "Sample",
        url = url,
        originalUrl = originalUrl,
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
    fun windowEdges_day0_active() {
        val today = LocalDate.parse("2026-11-20")
        val added = LocalDate.parse("2026-11-20")
        val index = NewBadgeIndex(mapOf("audio|example.com/site" to added))
        val entry = sampleEntry()
        assertEquals(added, index.activeDate(entry, today))
    }

    @Test
    fun windowEdges_day7_active() {
        val today = LocalDate.parse("2026-11-20")
        val added = LocalDate.parse("2026-11-13")
        val index = NewBadgeIndex(mapOf("audio|example.com/site" to added))
        val entry = sampleEntry()
        assertEquals(added, index.activeDate(entry, today))
    }

    @Test
    fun windowEdges_dayMinus1_active() {
        // Site date is 1 day ahead of device
        val today = LocalDate.parse("2026-11-20")
        val added = LocalDate.parse("2026-11-21")
        val index = NewBadgeIndex(mapOf("audio|example.com/site" to added))
        val entry = sampleEntry()
        assertEquals(added, index.activeDate(entry, today))
    }

    @Test
    fun windowEdges_day8_inactive() {
        val today = LocalDate.parse("2026-11-20")
        val added = LocalDate.parse("2026-11-12")
        val index = NewBadgeIndex(mapOf("audio|example.com/site" to added))
        val entry = sampleEntry()
        assertNull(index.activeDate(entry, today))
    }

    @Test
    fun windowEdges_dayMinus2_inactive() {
        val today = LocalDate.parse("2026-11-20")
        val added = LocalDate.parse("2026-11-22")
        val index = NewBadgeIndex(mapOf("audio|example.com/site" to added))
        val entry = sampleEntry()
        assertNull(index.activeDate(entry, today))
    }

    @Test
    fun pageScoping_sameUrlDifferentSection() {
        val today = LocalDate.parse("2026-11-20")
        val added = LocalDate.parse("2026-11-19")
        // Mark exists only for section 'audio'
        val index = NewBadgeIndex(mapOf("audio|example.com/site" to added))

        val audioEntry = sampleEntry(sectionId = "audio")
        val videoEntry = sampleEntry(sectionId = "video")

        assertEquals(added, index.activeDate(audioEntry, today))
        assertNull(index.activeDate(videoEntry, today))
    }

    @Test
    fun originalUrlMatch_whenPrimaryUrlNotMarked() {
        val today = LocalDate.parse("2026-11-20")
        val added = LocalDate.parse("2026-11-18")
        val index = NewBadgeIndex(mapOf("audio|rentry.co/FMHYB64#wavernrs" to added))

        // entry.url is decoded/base64-redirected, originalUrl holds the source link
        val entry = sampleEntry(
            sectionId = "audio",
            url = "https://unmarked.example/dest",
            originalUrl = "https://rentry.co/FMHYB64#wavernrs",
        )

        assertEquals(added, index.activeDate(entry, today))
    }

    @Test
    fun originalUrlMatch_whenPrimaryUrlMarked_prefersPrimary() {
        val today = LocalDate.parse("2026-11-20")
        val primaryDate = LocalDate.parse("2026-11-19")
        val origDate = LocalDate.parse("2026-11-15")
        val index = NewBadgeIndex(
            mapOf(
                "audio|example.com/site" to primaryDate,
                "audio|original.example/site" to origDate,
            ),
        )

        val entry = sampleEntry(
            sectionId = "audio",
            url = "https://example.com/site",
            originalUrl = "https://original.example/site",
        )

        assertEquals(primaryDate, index.activeDate(entry, today))
    }

    @Test
    fun jsonRoundTrip_preservesMarksAndState() {
        val state = NewBadgeState(
            version = 1,
            checkedAt = 1000L,
            fetchedAt = 1000L,
            pages = mapOf(
                "audio" to PageState(
                    mdSha = "sha1",
                    bodySha = "bsha1",
                    dirty = 0,
                    fetchedAt = 1000L,
                    marks = listOf(
                        BadgeMark("example.com/site", "2026-11-17"),
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<NewBadgeState>(encoded)
        assertEquals(state, decoded)

        val index = decoded.toIndex()
        val today = LocalDate.parse("2026-11-20")
        val entry = sampleEntry(sectionId = "audio", url = "https://example.com/site")
        assertEquals(LocalDate.parse("2026-11-17"), index.activeDate(entry, today))
    }

    @Test
    fun version2_treatedAsEmpty() {
        val state = NewBadgeState(
            version = 2,
            checkedAt = 1000L,
            fetchedAt = 1000L,
            pages = mapOf(
                "audio" to PageState(
                    mdSha = "sha1",
                    bodySha = "bsha1",
                    dirty = 0,
                    fetchedAt = 1000L,
                    marks = listOf(BadgeMark("example.com/site", "2026-11-17")),
                ),
            ),
        )
        val index = state.toIndex()
        assertEquals(NewBadgeIndex.EMPTY, index)
    }
}
