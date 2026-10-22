package io.github.omeg4dev.fmhy.ui.search

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.search.QueryEdit
import io.github.omeg4dev.fmhy.data.search.QueryParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFieldLogicTest {
    private val sections = listOf(Section("video", "Video", "", 1, 3), Section("tools", "Tools", "", 2, 3), Section("anime", "Movies / TV / Anime", "", 3, 3))

    private fun typed(pillsRaw: String, free: String, newText: String, caret: Int = newText.length): FieldEdit {
        val pills = QueryEdit.split(pillsRaw).pills
        return applyFieldEdit(pills, fieldValueFor(free), TextFieldValue(SENTINEL + newText, TextRange(caret + 1)))
    }

    @Test
    fun plainTypingKeepsTheValueUntouched() {
        val e = typed("", "ani", "anim")
        assertEquals("anim", e.raw)
        assertEquals(SENTINEL + "anim", e.value.text)
    }

    @Test
    fun spaceAfterAParameterMovesItIntoAPill() {
        val e = typed("", "anime in:video", "anime in:video ")
        assertEquals("in:video anime ", e.raw)
        assertEquals(SENTINEL + "anime ", e.value.text)
        assertEquals(TextRange(7), e.value.selection)
    }

    @Test
    fun existingPillsStayInFrontWhileTyping() {
        val e = typed("is:starred ", "", "x")
        assertEquals("is:starred x", e.raw)
    }

    @Test
    fun backspaceAtTheStartRemovesTheLastPill() {
        val pills = QueryEdit.split("in:video is:starred anime").pills
        val old = TextFieldValue(SENTINEL + "anime", TextRange(1))
        val e = applyFieldEdit(pills, old, TextFieldValue("anime", TextRange(0)))
        assertEquals("in:video anime", e.raw)
        assertEquals(SENTINEL + "anime", e.value.text)
        assertEquals(TextRange(1), e.value.selection)
    }

    @Test
    fun backspaceWithoutPillsJustKeepsTheText() {
        val old = TextFieldValue(SENTINEL + "anime", TextRange(1))
        val e = applyFieldEdit(emptyList(), old, TextFieldValue("anime", TextRange(0)))
        assertEquals("anime", e.raw)
    }

    @Test
    fun pastingParametersCommitsThem() {
        val e = typed("", "", "in:tools -x foo ")
        assertEquals("in:tools -x foo ", e.raw)
        assertEquals(listOf("in:tools", "-x"), QueryEdit.split(e.raw).pills.map { it.text })
    }

    @Test
    fun labels() {
        fun l(raw: String) = pillLabel(QueryParser.tokens(raw).first(), sections)
        assertEquals("in Video", l("in:video"))
        assertEquals("in Tools", l("in:TOOLS"))
        assertEquals("in nope", l("in:nope"))
        assertEquals("in Anime", l("in:anime"))
        assertEquals("Starred", l("is:starred"))
        assertEquals("Top picks", l("is:top"))
        assertEquals("Hidden links", l("is:b64"))
        assertEquals("site github.com", l("site:www.github.com"))
        assertEquals("not foo", l("-foo"))
        assertEquals("not a b", l("-\"a b\""))
    }
}
