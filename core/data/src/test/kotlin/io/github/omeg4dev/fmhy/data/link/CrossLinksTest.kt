package io.github.omeg4dev.fmhy.data.link

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.SectionItem
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrossLinksTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun heading(section: String, title: String, anchor: String) =
        Heading("$section/$anchor", section, null, 2, title, anchor, 0)

    private val links = CrossLinks(
        listOf(
            heading("audio", "Spotify Tools", "spotify-tools"),
            heading("social-media-tools", "Twitch Adblockers", "twitch-adblockers"),
            heading("linux-macos", "Mac Adblock / Privacy", "mac-adblock-privacy"),
            heading("gaming", "Download Games", "download-games"),
        )
    )

    @Test
    fun splitLinkTitle() {
        assertEquals("Android Adblocking" to "https://x.example/a#b", CrossLinks.splitLinkTitle("[Android Adblocking](https://x.example/a#b)"))
        assertEquals("Plain" to null, CrossLinks.splitLinkTitle("Plain"))
    }

    @Test
    fun resolvesRedditWikiLinks() {
        assertEquals(
            LinkTarget("audio", "audio/spotify-tools"),
            links.resolve("https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/audio/#wiki_.25BA_spotify_tools"),
        )
        assertEquals(
            LinkTarget("social-media-tools", "social-media-tools/twitch-adblockers"),
            links.resolve("https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/social-media#wiki_.25B7_twitch_adblockers"),
        )
        assertEquals(
            LinkTarget("linux-macos", "linux-macos/mac-adblock-privacy"),
            links.resolve("https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/linux#wiki_.25B7_mac_adblock_.2F_privacy"),
        )
        // Page without anchor → the section itself.
        assertEquals(LinkTarget("audio", null), links.resolve("https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/audio/"))
    }

    @Test
    fun resolvesFmhyNetLinksAndRejectsOthers() {
        assertEquals(LinkTarget("gaming", "gaming/download-games"), links.resolve("https://fmhy.net/gaming#download-games"))
        assertNull(links.resolve("https://fmhy.net/posts/search"))
        assertNull(links.resolve("https://pastes.fmhy.net/"))
        assertNull(links.resolve("https://github.com/fmhy/edit"))
    }

    /** Every cross-reference in the real dataset should land on an in-app heading. */
    @Test
    fun bundledSnapshotCrossReferencesResolve() = runTest {
        val snapshot = File("../../app/src/main/assets/dataset/snapshot.gzbin")
        assumeTrue("bundled snapshot not built", snapshot.exists())
        val dbFile = tempFolder.newFile("snapshot.db")
        java.util.zip.GZIPInputStream(snapshot.inputStream()).use { input ->
            dbFile.outputStream().use { input.copyTo(it) }
        }
        val store = DatasetStore(dbFile)
        val resolver = CrossLinks(store.allHeadings())

        val urls = mutableListOf<String>()
        for (section in store.sections()) {
            for (item in store.sectionItems(section.id)) {
                when (item) {
                    is SectionItem.HeadingItem -> CrossLinks.splitLinkTitle(item.heading.title).second?.let(urls::add)
                    is SectionItem.EntryItem -> item.entry.url.takeIf { "FREEMEDIAHECKYEAH/wiki/" in it || "//fmhy.net/" in it }?.let(urls::add)
                    else -> Unit
                }
            }
        }
        val internal = urls.filter { "#" in it }   // links that name a heading
        val missed = internal.filter { resolver.resolve(it)?.headingId == null }
        println("cross-refs with anchor: ${internal.size}, unresolved: ${missed.size}")
        missed.distinct().forEach { println("  unresolved: $it") }
        assertTrue("too few cross-refs found: ${internal.size}", internal.size > 300)
        assertTrue("unresolved ${missed.size} of ${internal.size}", missed.size * 10 <= internal.size)
        store.close()
    }
}
