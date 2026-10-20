package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NoteInterceptionTest {

    @Test
    fun extractNoteInfo_matchesGithubNoteUrl() {
        val url = "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/CodeRabbit.md"
        val info = extractNoteInfo(url)
        assertNotNull(info)
        assertEquals(".vitepress/notes/CodeRabbit.md", info!!.first)
        assertEquals("CodeRabbit", info.second)
    }

    @Test
    fun extractNoteInfo_matchesHyphenatedNoteUrl() {
        val url = "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/steam-controller-support.md"
        val info = extractNoteInfo(url)
        assertNotNull(info)
        assertEquals(".vitepress/notes/steam-controller-support.md", info!!.first)
        assertEquals("Steam Controller Support", info.second)
    }

    @Test
    fun extractNoteInfo_ignoresNonNoteUrl() {
        val url = "https://github.com/fmhy/edit/blob/main/docs/audio.md"
        assertNull(extractNoteInfo(url))

        val extUrl = "https://www.coderabbit.ai/"
        assertNull(extractNoteInfo(extUrl))
    }

    @Test
    fun formatNoteTitle_formatsProperly() {
        assertEquals("Winrar", formatNoteTitle("winrar.md"))
        assertEquals("Tld List Note", formatNoteTitle("tld-list-note.md"))
        assertEquals("CodeRabbit", formatNoteTitle("CodeRabbit.md"))
    }
}
