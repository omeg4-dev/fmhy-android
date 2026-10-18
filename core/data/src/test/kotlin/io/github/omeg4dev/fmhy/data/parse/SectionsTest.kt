package io.github.omeg4dev.fmhy.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {

    @Test
    fun testParseSectionsExclusionsAndOrdering() {
        val ts = """
            export const headers: Header = {
              'privacy.md': {
                title: 'Adblocking / Privacy',
                description: 'Adblocking, Privacy'
              },
              'beginners-guide.md': {
                title: 'Beginners Guide',
                description: 'A Guide for Beginners'
              },
              'downloading.md': {
                title: 'Downloading',
                description: 'Download Sites'
              },
              'base64.md': {
                title: 'Base64',
                description: 'Storage'
              },
              'readme.md': {
                title: 'Readme',
                description: 'Readme info'
              },
              'recently-removed.md': {
                title: 'Recently Removed',
                description: 'Removed'
              }
            }
        """.trimIndent()

        val sections = Sections.parse(ts)
        assertEquals(2, sections.size)
        assertEquals("privacy", sections[0].id)
        assertEquals(0, sections[0].ord)
        assertEquals("Adblocking / Privacy", sections[0].title)

        assertEquals("downloading", sections[1].id)
        assertEquals(2, sections[1].ord)
        assertEquals("Downloading", sections[1].title)
    }
}
