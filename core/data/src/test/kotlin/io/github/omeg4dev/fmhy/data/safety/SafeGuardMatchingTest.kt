package io.github.omeg4dev.fmhy.data.safety

import androidx.sqlite.execSQL
import io.github.omeg4dev.fmhy.data.DatasetStore
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafeGuardMatchingTest {

    private lateinit var dbFile: File
    private lateinit var store: DatasetStore
    private lateinit var safeGuard: SafeGuard

    @Before
    fun setUp() = runTest {
        dbFile = File.createTempFile("safeguard_matching_test", ".db")
        DatasetStore.initDatabase(dbFile)
        store = DatasetStore(dbFile)
        safeGuard = SafeGuard(store)

        // Seed basic section
        store.withConnection { conn ->
            conn.execSQL("INSERT INTO sections (id, title, description, file, ord) VALUES ('video', 'Video', '', 'video.md', 1);")
        }
    }

    @After
    fun tearDown() = runTest {
        store.close()
        dbFile.delete()
    }

    private suspend fun insertEntry(
        num: Int,
        id: String,
        name: String,
        url: String,
        tier: Int = 0,
        mirrorsJson: String = "[]",
        extraLinksJson: String = "[]",
    ) {
        store.withConnection { conn ->
            val sql = """
                INSERT INTO entries (
                    num, id, section_id, heading_id, ord, name, url, original_url,
                    description, tags, heading_path, tier, kind, line, is_inline_more, is_base64,
                    mirrors, extra_links, first_seen, last_changed, starred_at
                ) VALUES (?, ?, 'video', NULL, ?, ?, ?, NULL, '', '', 'Video › Streaming', ?, 0, 1, 0, 0, ?, ?, 1000, 1000, NULL)
            """.trimIndent()
            conn.prepare(sql).use { stmt ->
                stmt.bindLong(1, num.toLong())
                stmt.bindText(2, id)
                stmt.bindLong(3, num.toLong())
                stmt.bindText(4, name)
                stmt.bindText(5, url)
                stmt.bindLong(6, tier.toLong())
                stmt.bindText(7, mirrorsJson)
                stmt.bindText(8, extraLinksJson)
                stmt.step()
            }
        }
    }

    @Test
    fun testBareDomainOfPathListedEntryIsDomainListed() = runTest {
        insertEntry(1, "path-entry", "Path Entry", "https://example.org/app")

        val verdict = safeGuard.check("https://example.org")
        assertEquals(SafetyStatus.DOMAIN_LISTED, verdict.status)
        assertEquals(1, verdict.matches.size)
        assertEquals("path-entry", verdict.matches[0].id)
    }

    @Test
    fun testMirrorUrlMatchesListedWithViaMirror() = runTest {
        insertEntry(
            num = 2,
            id = "mirror-entry",
            name = "Mirror Entry",
            url = "https://primary-site.com",
            mirrorsJson = """[{"label":"Mirror 1","url":"https://mirror-site.org/app"}]""",
        )

        val verdict = safeGuard.check("https://mirror-site.org/app")
        assertEquals(SafetyStatus.LISTED, verdict.status)
        assertEquals(Via.MIRROR, verdict.via)
        assertEquals(1, verdict.matches.size)
        assertEquals("mirror-entry", verdict.matches[0].id)
    }

    @Test
    fun testExtraLinkMatchesViaExtra() = runTest {
        insertEntry(
            num = 3,
            id = "extra-entry",
            name = "Extra Entry",
            url = "https://primary-extra.com",
            extraLinksJson = """[{"label":"Userscript","url":"https://extra-link.org/download"}]""",
        )

        val verdict = safeGuard.check("https://extra-link.org/download")
        assertEquals(SafetyStatus.LISTED, verdict.status)
        assertEquals(Via.EXTRA, verdict.via)
        assertEquals(1, verdict.matches.size)
        assertEquals("extra-entry", verdict.matches[0].id)
    }

    @Test
    fun testSubdomainListedParentCheckedIsDomainListed() = runTest {
        insertEntry(4, "sub-entry", "Sub Entry", "https://app.myservice.org")

        val verdict = safeGuard.check("https://myservice.org")
        assertEquals(SafetyStatus.DOMAIN_LISTED, verdict.status)
        assertEquals(1, verdict.matches.size)
        assertEquals("sub-entry", verdict.matches[0].id)
    }

    @Test
    fun testGithubUnlistedRepoReturnsNoDataWithSharedHostListedCount() = runTest {
        insertEntry(5, "gh-1", "Repo One", "https://github.com/org1/repo1")
        insertEntry(6, "gh-2", "Repo Two", "https://github.com/org2/repo2")

        val verdict = safeGuard.check("https://github.com/unlisted/repo")
        assertEquals(SafetyStatus.NO_DATA, verdict.status)
        assertEquals(2, verdict.sharedHostListedCount)
    }

    @Test
    fun testLookalikesReturnedForFmovies() = runTest {
        insertEntry(7, "fmovies-ps", "FMovies", "https://fmovies.ps")

        val verdict = safeGuard.check("https://fmovies-hd.to")
        assertEquals(SafetyStatus.NO_DATA, verdict.status)
        assertTrue("Lookalikes should contain fmovies.ps", verdict.lookalikes.any { it.url == "https://fmovies.ps" })
    }

    @Test
    fun testUnderscoreAndPercentDoNotWildcard() = runTest {
        insertEntry(8, "hyphen-entry", "Hyphen Entry", "https://my-hyphen-site.com")
        insertEntry(9, "under-entry", "Under Entry", "https://my_under_site.com")

        // Underscore in checked host should NOT match hyphen entry
        val verdictUnderscore = safeGuard.check("https://my_hyphen-site.com")
        assertEquals(SafetyStatus.NO_DATA, verdictUnderscore.status)

        // Matching literal underscore entry
        val verdictExactUnder = safeGuard.check("https://my_under_site.com")
        assertEquals(SafetyStatus.LISTED, verdictExactUnder.status)
        assertEquals("under-entry", verdictExactUnder.matches[0].id)
    }
}
