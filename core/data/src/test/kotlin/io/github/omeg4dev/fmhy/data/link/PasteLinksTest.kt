package io.github.omeg4dev.fmhy.data.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasteLinksTest {
    @Test
    fun rawUrlOnlyForPastebin() {
        assertEquals("https://pastebin.com/raw/hvyWqiqc", PasteLinks.rawUrl("https://pastebin.com/hvyWqiqc"))
        assertEquals("https://pastebin.com/raw/hvyWqiqc", PasteLinks.rawUrl("https://pastebin.com/raw/hvyWqiqc"))
        assertNull(PasteLinks.rawUrl("https://rentry.co/IPTV-Playlists"))
        assertNull(PasteLinks.rawUrl("https://pastebin.com/u/someone/1"))
    }

    @Test
    fun decodesSingleUrl() {
        // real ESET-style paste body: one base64 line → one URL
        assertEquals(listOf("https://firehawk52.com/"), PasteLinks.decode("aHR0cHM6Ly9maXJlaGF3azUyLmNvbS8="))
    }

    @Test
    fun decodesSeveralUrls() {
        assertEquals(
            listOf("https://t.me/EsetKeyRobot", "https://t.me/s/esetnodkeys"),
            PasteLinks.decode("aHR0cHM6Ly90Lm1lL0VzZXRLZXlSb2JvdAoKaHR0cHM6Ly90Lm1lL3MvZXNldG5vZGtleXM="),
        )
    }

    @Test
    fun decodesNestedBase64() {
        val once = java.util.Base64.getEncoder().encodeToString("https://example.org/a".toByteArray())
        val twice = java.util.Base64.getEncoder().encodeToString(once.toByteArray())
        assertEquals(listOf("https://example.org/a"), PasteLinks.decode(twice))
    }

    @Test
    fun plainTextWithoutLinksGivesNothing() {
        assertEquals(emptyList<String>(), PasteLinks.decode("hello world, not base64!"))
    }
}
