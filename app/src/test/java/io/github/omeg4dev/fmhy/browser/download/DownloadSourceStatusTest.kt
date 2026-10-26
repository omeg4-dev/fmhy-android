package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.data.safety.Via
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceStatusTest {

    private fun makeEntry(
        id: String,
        name: String = "Test Site",
        url: String = "https://example.com",
    ): Entry = Entry(
        id = id,
        num = 1,
        sectionId = "video",
        headingId = null,
        ord = 0,
        name = name,
        url = url,
        originalUrl = null,
        description = "",
        tags = emptyList(),
        headingPath = "Video",
        tier = Tier.NORMAL,
        isInlineMore = false,
        isBase64 = false,
        mirrors = emptyList(),
        extraLinks = emptyList(),
        firstSeen = 0L,
        lastChanged = 0L,
        starredAt = null,
    )

    private fun makeVerdict(
        status: SafetyStatus,
        url: String = "https://example.com",
        host: String = "example.com",
        matches: List<Entry> = emptyList(),
        via: Via? = null,
        lookalikes: List<Entry> = emptyList(),
    ): Verdict = Verdict(
        status = status,
        input = url,
        normalizedUrl = url,
        host = host,
        reason = null,
        matches = matches,
        via = via,
        lookalikes = lookalikes,
    )

    @Test
    fun testFileStarredPageNotListed() {
        val fileVerdict = makeVerdict(
            status = SafetyStatus.STARRED,
            url = "https://file.com/dl.zip",
            host = "file.com",
            matches = listOf(makeEntry("e_file")),
        )
        val pageVerdict = makeVerdict(
            status = SafetyStatus.NO_DATA,
            url = "https://page.com/item",
            host = "page.com",
        )

        val status = DownloadSourceStatus.from(
            fileVerdict = fileVerdict,
            pageVerdict = pageVerdict,
            fileHost = "file.com",
            pageHost = "page.com",
            wikiDateMs = 123456L,
        )

        assertEquals(SourceKind.STARRED, status.kind)
        assertEquals(SourceKind.STARRED, status.fileKind)
        assertEquals(SourceKind.NOT_LISTED, status.pageKind)
        assertTrue(status.differs)
        assertEquals("e_file", status.entryId)
        assertFalse(status.isMirror)
        assertEquals("file.com", status.fileHost)
        assertEquals("page.com", status.pageHost)
        assertEquals(123456L, status.wikiDateMs)
    }

    @Test
    fun testFileNotListedPageStarred() {
        val fileVerdict = makeVerdict(
            status = SafetyStatus.NO_DATA,
            url = "https://file.com/dl.zip",
            host = "file.com",
        )
        val pageVerdict = makeVerdict(
            status = SafetyStatus.STARRED,
            url = "https://page.com/item",
            host = "page.com",
            matches = listOf(makeEntry("e_page")),
        )

        val status = DownloadSourceStatus.from(
            fileVerdict = fileVerdict,
            pageVerdict = pageVerdict,
            fileHost = "file.com",
            pageHost = "page.com",
            wikiDateMs = null,
        )

        // File side wins for listed/starred -> file is NOT_LISTED, so kind is NOT_LISTED
        assertEquals(SourceKind.NOT_LISTED, status.kind)
        assertEquals(SourceKind.NOT_LISTED, status.fileKind)
        assertEquals(SourceKind.STARRED, status.pageKind)
        assertTrue(status.differs)
        // Show in section falls back to page side entryId
        assertEquals("e_page", status.entryId)
        assertFalse(status.isMirror)
    }

    @Test
    fun testPageUnsafeFileListed() {
        val fileVerdict = makeVerdict(
            status = SafetyStatus.LISTED,
            url = "https://file.com/dl.zip",
            host = "file.com",
            matches = listOf(makeEntry("e_file")),
        )
        val pageVerdict = makeVerdict(
            status = SafetyStatus.UNSAFE,
            url = "https://page.com/item",
            host = "page.com",
        )

        val status = DownloadSourceStatus.from(
            fileVerdict = fileVerdict,
            pageVerdict = pageVerdict,
            fileHost = "file.com",
            pageHost = "page.com",
            wikiDateMs = null,
        )

        assertEquals(SourceKind.UNSAFE, status.kind)
        assertEquals(SourceKind.LISTED, status.fileKind)
        assertEquals(SourceKind.UNSAFE, status.pageKind)
        assertTrue(status.differs)
        assertEquals("e_file", status.entryId)
    }

    @Test
    fun testLookalikeOnFile() {
        val target = makeEntry("e_target", name = "TargetSite", url = "https://targetsite.com")
        val fileVerdict = makeVerdict(
            status = SafetyStatus.NO_DATA,
            url = "https://targets1te.com/dl.zip",
            host = "targets1te.com",
            lookalikes = listOf(target),
        )
        val pageVerdict = makeVerdict(
            status = SafetyStatus.NO_DATA,
            url = "https://page.com/item",
            host = "page.com",
        )

        val status = DownloadSourceStatus.from(
            fileVerdict = fileVerdict,
            pageVerdict = pageVerdict,
            fileHost = "targets1te.com",
            pageHost = "page.com",
            wikiDateMs = null,
        )

        assertEquals(SourceKind.LOOKALIKE, status.kind)
        assertEquals(SourceKind.LOOKALIKE, status.fileKind)
        assertEquals(SourceKind.NOT_LISTED, status.pageKind)
        assertEquals("TargetSite", status.lookalikeOf)
    }

    @Test
    fun testMirrorFlagFromViaMirror() {
        val fileVerdict = makeVerdict(
            status = SafetyStatus.LISTED,
            url = "https://mirror.com/file",
            host = "mirror.com",
            matches = listOf(makeEntry("e_mirror")),
            via = Via.MIRROR,
        )

        val status = DownloadSourceStatus.from(
            fileVerdict = fileVerdict,
            pageVerdict = null,
            fileHost = "mirror.com",
            pageHost = null,
            wikiDateMs = null,
        )

        assertEquals(SourceKind.LISTED, status.kind)
        assertTrue(status.isMirror)
        assertEquals("e_mirror", status.entryId)
    }

    @Test
    fun testPastedPageVerdictNull() {
        val fileVerdict = makeVerdict(
            status = SafetyStatus.LISTED,
            url = "https://file.com/dl.zip",
            host = "file.com",
            matches = listOf(makeEntry("e_file")),
        )

        val status = DownloadSourceStatus.from(
            fileVerdict = fileVerdict,
            pageVerdict = null,
            fileHost = "file.com",
            pageHost = null,
            wikiDateMs = null,
        )

        assertNull(status.pageHost)
        assertNull(status.pageKind)
        assertFalse(status.differs)
    }

    @Test
    fun testWwwOnlyHostDifference() {
        val fileVerdict = makeVerdict(
            status = SafetyStatus.LISTED,
            url = "https://example.com/dl.zip",
            host = "example.com",
            matches = listOf(makeEntry("e1")),
        )
        val pageVerdict = makeVerdict(
            status = SafetyStatus.LISTED,
            url = "https://www.example.com/page",
            host = "www.example.com",
            matches = listOf(makeEntry("e1")),
        )

        val status = DownloadSourceStatus.from(
            fileVerdict = fileVerdict,
            pageVerdict = pageVerdict,
            fileHost = "example.com",
            pageHost = "www.example.com",
            wikiDateMs = null,
        )

        assertFalse(status.differs)
        assertEquals("example.com", status.fileHost)
        assertEquals("www.example.com", status.pageHost)
    }

    @Test
    fun testJsonRoundTripWithWikiDateMs() {
        val original = DownloadSourceStatus(
            kind = SourceKind.STARRED,
            isMirror = true,
            fileHost = "cdn.example.com",
            pageHost = "example.com",
            differs = true,
            fileKind = SourceKind.STARRED,
            pageKind = SourceKind.LISTED,
            entryId = "entry-123",
            wikiDateMs = 1730000000000L,
            lookalikeOf = "OriginalSite",
        )

        val json = original.toJson()
        val restored = DownloadSourceStatus.fromJson(json)
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun testJsonRoundTripWithNulls() {
        val original = DownloadSourceStatus(
            kind = SourceKind.NOT_LISTED,
            isMirror = false,
            fileHost = "unknown.com",
            pageHost = null,
            differs = false,
            fileKind = SourceKind.NOT_LISTED,
            pageKind = null,
            entryId = null,
            wikiDateMs = null,
            lookalikeOf = null,
        )

        val json = original.toJson()
        val restored = DownloadSourceStatus.fromJson(json)
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun testBadJsonReturnsNull() {
        assertNull(DownloadSourceStatus.fromJson(null))
        assertNull(DownloadSourceStatus.fromJson(""))
        assertNull(DownloadSourceStatus.fromJson("   "))
        assertNull(DownloadSourceStatus.fromJson("{not valid json}"))
        assertNull(DownloadSourceStatus.fromJson("""{"kind":"UNKNOWN_KIND"}"""))
    }

    @Test
    fun testDownloadSourceText() {
        assertEquals("Starred on FMHY", DownloadSourceText.title(SourceKind.STARRED))
        assertEquals("Listed on FMHY", DownloadSourceText.title(SourceKind.LISTED))
        assertEquals("Not listed on FMHY", DownloadSourceText.title(SourceKind.NOT_LISTED))
        assertEquals("Listed as unsafe by FMHY", DownloadSourceText.title(SourceKind.UNSAFE))
        assertEquals("Known fake lookalike of a listed site", DownloadSourceText.title(SourceKind.LOOKALIKE))

        // Neutral "Not listed" wording: must not contain "unsafe" or "warning"
        val notListed = DownloadSourceText.title(SourceKind.NOT_LISTED)
        assertFalse(notListed.contains("unsafe", ignoreCase = true))
        assertFalse(notListed.contains("warning", ignoreCase = true))

        assertEquals("Starred", DownloadSourceText.badge(SourceKind.STARRED))
        assertEquals("Listed", DownloadSourceText.badge(SourceKind.LISTED))
        assertEquals("Not listed", DownloadSourceText.badge(SourceKind.NOT_LISTED))
        assertEquals("Unsafe", DownloadSourceText.badge(SourceKind.UNSAFE))
        assertEquals("Lookalike", DownloadSourceText.badge(SourceKind.LOOKALIKE))

        assertEquals(" (mirror of a listed site)", DownloadSourceText.mirrorSuffix)

        assertEquals(
            "The file comes from cdn.example.com, not from the page (example.com).",
            DownloadSourceText.differsLine("example.com", "cdn.example.com")
        )

        assertEquals(
            "FMHY status only says where a site is listed. It does not mean a file is safe.",
            DownloadSourceText.REMINDER
        )

        assertEquals("FMHY data date unknown", DownloadSourceText.dateLabel(null))

        val dateMs = LocalDate.of(2026, Month.OCTOBER, 23).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("FMHY data from 23 Oct", DownloadSourceText.dateLabel(dateMs, ZoneOffset.UTC))
    }
}
