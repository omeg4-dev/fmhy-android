package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.ui.graphics.Color
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.model.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteLinksTest {

    @Test
    fun noteIdOf_extractsIdFromGithubEditUrl() {
        assertEquals(
            "ubo-lite",
            noteIdOf("https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/ubo-lite.md"),
        )
    }

    @Test
    fun noteIdOf_ignoresQueryAndFragment() {
        val base = "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/ubo-lite.md"
        assertEquals("ubo-lite", noteIdOf("$base?plain=1"))
        assertEquals("ubo-lite", noteIdOf("$base#x"))
        assertEquals("ubo-lite", noteIdOf("$base?plain=1#x"))
    }

    @Test
    fun noteIdOf_returnsNullForNonNoteOrWithoutMdExtension() {
        assertNull(noteIdOf("https://example.com/"))
        assertNull(noteIdOf("https://github.com/fmhy/edit/blob/main/docs/audio.md"))
        assertNull(noteIdOf("https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/ubo-lite"))
    }

    @Test
    fun linkIconFor_usesNoteIconForNoteUrlsRegardlessOfLabel() {
        assertEquals(
            R.drawable.ic_link_note,
            linkIconFor(Link("Limits", "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/limits.md")),
        )
        assertNull(linkIconFor(Link("Warning", "https://example.com/warning")))
        assertEquals(
            R.drawable.ic_link_github,
            linkIconFor(Link("GitHub", "https://github.com/example/repo")),
        )
        assertEquals(
            R.drawable.ic_link_note,
            linkIconFor(Link("Note", "https://example.com/note")),
        )
    }

    @Test
    fun noteLinkTint_usesCriticalOnlyWhenNoteIdIsCritical() {
        val accent = Color(0xFF1E88E5)
        val critical = Color(0xFFB4214A)
        val criticalIds = setOf("ubo-lite")

        assertEquals(
            critical,
            noteLinkTint(
                url = "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/ubo-lite.md",
                criticalIds = criticalIds,
                accent = accent,
                critical = critical,
            ),
        )
        assertEquals(
            accent,
            noteLinkTint(
                url = "https://github.com/fmhy/edit/blob/main/docs/.vitepress/notes/other-note.md",
                criticalIds = criticalIds,
                accent = accent,
                critical = critical,
            ),
        )
        assertEquals(
            accent,
            noteLinkTint(
                url = "https://example.com/",
                criticalIds = criticalIds,
                accent = accent,
                critical = critical,
            ),
        )
    }
}
