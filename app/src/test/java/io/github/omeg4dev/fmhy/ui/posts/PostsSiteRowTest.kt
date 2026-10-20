package io.github.omeg4dev.fmhy.ui.posts

import io.github.omeg4dev.fmhy.ui.common.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PostsSiteRowTest {
    @Test
    fun boldLinkWithDashDescription() {
        val r = parsePostSiteRow("**[Foo](https://foo.example/)** - Great streaming site")!!
        assertEquals("Foo", r.name)
        assertEquals("https://foo.example/", r.url)
        assertEquals("Great streaming site", r.description)
    }

    @Test
    fun plainLinkWithEnDashAndComma() {
        assertEquals("a tool", parsePostSiteRow("[Bar](https://bar.example) – a tool")!!.description)
        assertEquals("a tool", parsePostSiteRow("[Bar](https://bar.example), a tool")!!.description)
    }

    @Test
    fun balancedParenthesesInUrl() {
        val r = parsePostSiteRow("[Wiki](https://en.wikipedia.org/wiki/Foo_(bar)) - page")!!
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", r.url)
        assertEquals("page", r.description)
    }

    @Test
    fun notALinkFirst() {
        assertNull(parsePostSiteRow("Added **[Foo](https://foo.example)** today"))
        assertNull(parsePostSiteRow("just text"))
        assertNull(parsePostSiteRow("[unclosed(https://x.y"))
    }

    @Test
    fun noDescription() {
        assertEquals("", parsePostSiteRow("**[Foo](https://foo.example)**")!!.description)
    }

    @Test
    fun groupsByHeading() {
        val blocks = parseMarkdownBlocks("intro\n\n## One\n\n- a\n- b\n\n## Two\n\n- c\n")
        val sections = groupPostBlocks(blocks)
        assertEquals(3, sections.size)
        assertNull(sections[0].heading)
        assertNotNull(sections[1].heading)
        assertEquals(2, sections[1].blocks.size)
        assertEquals("Two", sections[2].heading!!.text)
    }
}
