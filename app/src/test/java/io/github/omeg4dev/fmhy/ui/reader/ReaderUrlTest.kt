package io.github.omeg4dev.fmhy.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderUrlTest {

    @Test
    fun fmhyWebUrlForPath_formatsCorrectly() {
        assertEquals("https://fmhy.net/beginners-guide", fmhyWebUrlForPath("beginners-guide.md"))
        assertEquals("https://fmhy.net/other/FAQ", fmhyWebUrlForPath("other/FAQ.md"))
        assertEquals("https://fmhy.net/other/backups", fmhyWebUrlForPath("other/backups.md"))
        assertEquals("https://fmhy.net/posts/april-2026", fmhyWebUrlForPath("posts/april-2026.md"))
        assertEquals("https://fmhy.net/posts/april-2026", fmhyWebUrlForPath("docs/posts/april-2026.md"))
    }
}
